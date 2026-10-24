package com.oneshop.service.fulfillment;

import com.oneshop.dto.response.FulfillmentDetailResponse;
import com.oneshop.dto.response.FulfillmentHistoryResponse;
import com.oneshop.dto.response.FulfillmentQueueItemResponse;
import com.oneshop.dto.response.OrderItemResponse;
import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.Order;
import com.oneshop.entity.OrderFulfillment;
import com.oneshop.entity.OrderFulfillmentStatus;
import com.oneshop.entity.OrderFulfillmentStatusHistory;
import com.oneshop.entity.OrderItem;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.FulfillmentConflictException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.OrderFulfillmentRepository;
import com.oneshop.repository.OrderFulfillmentStatusHistoryRepository;
import com.oneshop.repository.OrderItemRepository;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.PaymentDeadlinePolicy;
import com.oneshop.service.PaymentService;
import com.oneshop.service.PickupCodeService;
import com.oneshop.service.StaffStoreScopeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Staff operations on Fulfillments (V3-BE-04). The unit of work is one {@code order_fulfillments} row; its Store, not
 * {@code orders.store_id}, decides who may see and change it, and the Store must be one of the Staff's ACTIVE
 * assignments. Staff never change an Order: {@link OrderStatusAggregator} derives it in the same transaction.
 *
 * <p>Write path, always in this order: authorize the Store of the Fulfillment, lock the Order, lock the Fulfillment,
 * re-check the Store on the locked row, check the payment gate and the state machine, apply side effects (pickup code,
 * offline payment collection), write status and history, recompute the Order. Any refusal throws before the first
 * write, or rolls everything back, so a refused step never leaves a half-done change. Nothing here touches
 * {@code store_products} or {@code inventory_movements}: stock was deducted when the Order was created.
 */
@Service
public class StaffFulfillmentService {

    public static final int PAGE_SIZE = 20;
    private static final List<OrderFulfillmentStatus> OPEN_STATUSES = List.of(OrderFulfillmentStatus.CONFIRMED,
            OrderFulfillmentStatus.PREPARING, OrderFulfillmentStatus.PACKED, OrderFulfillmentStatus.SHIPPING,
            OrderFulfillmentStatus.READY_FOR_PICKUP);
    private static final Pattern TRACKING = Pattern.compile("[A-Za-z0-9._\\-]{3,50}");
    private static final LocalDateTime MIN_TIME = LocalDateTime.of(1900, 1, 1, 0, 0);
    private static final LocalDateTime MAX_TIME = LocalDateTime.of(9000, 1, 1, 0, 0);
    private static final int MIN_REASON = 5;
    private static final int MAX_REASON = 500;
    private static final int CODE_ATTEMPTS = 8;

    private final StaffStoreScopeService scope;
    private final OrderRepository orders;
    private final OrderFulfillmentRepository fulfillments;
    private final OrderFulfillmentStatusHistoryRepository histories;
    private final OrderItemRepository items;
    private final UserRepository users;
    private final PaymentService payments;
    private final PickupCodeService codes;
    private final PickupAttemptLimiter limiter;
    private final OrderStatusAggregator aggregator;
    private final PaymentDeadlinePolicy gate;

    public StaffFulfillmentService(StaffStoreScopeService scope, OrderRepository orders,
                                   OrderFulfillmentRepository fulfillments,
                                   OrderFulfillmentStatusHistoryRepository histories, OrderItemRepository items,
                                   UserRepository users, PaymentService payments, PickupCodeService codes,
                                   PickupAttemptLimiter limiter, OrderStatusAggregator aggregator,
                                   PaymentDeadlinePolicy gate) {
        this.scope = scope;
        this.orders = orders;
        this.fulfillments = fulfillments;
        this.histories = histories;
        this.items = items;
        this.users = users;
        this.payments = payments;
        this.codes = codes;
        this.limiter = limiter;
        this.aggregator = aggregator;
        this.gate = gate;
    }

    // ------------------------------------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public Page<FulfillmentQueueItemResponse> getQueue(FulfillmentQueueFilter filter, int page) {
        // 2-arg entry point: the exact shape the queue page uses without a customer keyword.
        return getQueue(filter, page, null);
    }

    /** Queue of the assigned Stores narrowed by an optional customer keyword (name / e-mail / receiver). */
    @Transactional(readOnly = true)
    public Page<FulfillmentQueueItemResponse> getQueue(FulfillmentQueueFilter filter, int page, String customer) {
        FulfillmentQueueFilter f = filter == null ? FulfillmentQueueFilter.none() : filter;
        List<Long> assigned = scope.getAssignedStoreIds();
        List<Long> storeIds = assigned;
        if (f.storeId() != null) {
            if (!assigned.contains(f.storeId())) {
                throw new AccessDeniedException("Bạn không được phân công tại chi nhánh này.");
            }
            storeIds = List.of(f.storeId());
        }
        Collection<OrderFulfillmentStatus> statuses = f.status() != null ? List.of(f.status())
                : f.openOnly() ? OPEN_STATUSES : List.of(OrderFulfillmentStatus.values());
        Collection<FulfillmentType> types = f.type() != null ? List.of(f.type())
                : List.of(FulfillmentType.values());
        var range = com.oneshop.service.query.SqlDayRange.of(f.from(), f.to());
        LocalDateTime from = f.from() == null ? MIN_TIME : range.fromInclusive();
        LocalDateTime to = f.to() == null ? MAX_TIME : range.toExclusive();
        var words = com.oneshop.service.search.TextSearch.words(customer);
        if (!words.isEmpty()) {
            var matched = fulfillments.searchForStaff(storeIds, statuses, types,
                            f.orderId() == null ? 0L : f.orderId(), from, to, "",
                            org.springframework.data.domain.Pageable.unpaged())
                    .getContent().stream()
                    .filter(row -> com.oneshop.service.search.TextSearch.matchesAll(words, row.getOrder().getUser().getFullName(),
                            row.getOrder().getUser().getEmail(), row.getOrder().getReceiverName(),
                            row.getOrder().getReceiverPhone()))
                    .toList();
            return toQueueItems(com.oneshop.service.search.TextSearch.slice(matched, com.oneshop.service.query.Paging.page(page, PAGE_SIZE)));
        }
        String pattern = likePattern(customer);
        Page<OrderFulfillment> rows = fulfillments.searchForStaff(storeIds, statuses, types,
                f.orderId() == null ? 0L : f.orderId(), from, to,
                pattern, com.oneshop.service.query.Paging.page(page, PAGE_SIZE));
        return toQueueItems(rows);
    }

    /** lower-case contains pattern with ! as the escape character; empty for an empty or blank keyword. */
    private static String likePattern(String customer) {
        var text = customer == null ? "" : customer.trim().toLowerCase();
        if (text.isEmpty()) {
            return "";
        }
        return "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    /** The most recent Fulfillments of the given (already authorized) Stores, for the dashboard. */
    @Transactional(readOnly = true)
    public List<FulfillmentQueueItemResponse> getRecent(Collection<Long> storeIds, int size) {
        if (storeIds == null || storeIds.isEmpty()) {
            return List.of();
        }
        return toQueueItems(fulfillments.searchForStaff(storeIds, OPEN_STATUSES,
                List.of(FulfillmentType.values()), 0L, MIN_TIME, MAX_TIME, com.oneshop.service.query.Paging.page(0, size))).getContent();
    }

    @Transactional(readOnly = true)
    public FulfillmentDetailResponse getFulfillment(Long fulfillmentId) {
        List<Long> assigned = scope.getAssignedStoreIds();
        requireInScope(fulfillmentId, assigned);
        OrderFulfillment f = fulfillments.findByIdAndStoreIdIn(fulfillmentId, assigned)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phần giao hàng."));
        Order order = f.getOrder();
        List<OrderItem> lines = items.findByFulfillmentIdInOrderByIdAsc(List.of(f.getId()));
        FulfillmentQueueItemResponse summary = toItem(f, lines);
        BigDecimal subtotal = lines.stream().map(OrderItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean collecting = order.getPaymentStatus() == OrderPaymentStatus.UNPAID
                && order.getPaymentMethod() != PaymentMethod.ONLINE
                && summary.actions().stream().anyMatch(FulfillmentAction::isCompletion);
        List<FulfillmentHistoryResponse> history = histories.findByFulfillmentIdOrderByChangedAtAscIdAsc(f.getId())
                .stream().map(h -> new FulfillmentHistoryResponse(h.getOldStatus(), h.getNewStatus(),
                        h.getChangedBy() == null ? "Hệ thống" : h.getChangedBy().getFullName(), redactPickupCode(h.getNote(), f),
                        h.getChangedAt())).toList();
        boolean delivery = f.getFulfillmentType() == FulfillmentType.DELIVERY;
        return new FulfillmentDetailResponse(summary, order.getReceiverPhone(),
                delivery ? order.getShippingAddress() : null, f.getTrackingCode(), f.getPickupCode() != null,
                subtotal, collecting ? order.getTotalAmount() : null, collecting ? order.getTotalAmount() : null,
                redactPickupCode(f.getNote(), f), redactPickupCode(f.getCancelReason(), f), f.getPreparedAt(), f.getPackedAt(), f.getShippedAt(), f.getReadyAt(),
                f.getCompletedAt(), lines.stream().map(i -> new OrderItemResponse(i.getProductName(),
                        i.getUnitPrice(), i.getQuantity(), i.getSubtotal())).toList(), history);
    }

    private static String redactPickupCode(String text, OrderFulfillment fulfillment) {
        String code = fulfillment.getPickupCode();
        return text == null || code == null || code.isBlank() ? text : text.replace(code, "[đã ẩn mã nhận]");
    }

    // ----------------------------------------------------------------------------------------------- writes

    /**
     * @param input tracking code for SHIP (optional), pickup code for COMPLETE_PICKUP (required), reason for the
     *              failure actions (required, 5 to 500 characters); ignored otherwise
     */
    @Transactional
    public void perform(Long fulfillmentId, FulfillmentAction action, String input) {
        if (action == null) {
            throw new BadRequestException("Thiếu thao tác xử lý.");
        }
        if (fulfillmentId == null) {
            throw new ResourceNotFoundException("Không tìm thấy phần giao hàng.");
        }
        List<Long> assigned = scope.getAssignedStoreIds(); // fails closed for an unassigned or invalid account
        OrderFulfillmentRepository.FulfillmentKey key = requireInScope(fulfillmentId, assigned);

        // Lock order is Order, then Fulfillment: the same order every other writer (cancel, payment) uses.
        Order order = orders.findByIdForUpdate(key.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        OrderFulfillment f = fulfillments.findByIdForUpdate(fulfillmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phần giao hàng."));
        if (!f.getOrder().getId().equals(order.getId())) {
            throw new FulfillmentConflictException(FulfillmentConflictException.INCONSISTENT_FULFILLMENT,
                    "Dữ liệu phần giao không nhất quán. Vui lòng tải lại trang.");
        }
        scope.requireAssignedStore(f.getStore().getId()); // the locked row's actual Store, before any write
        User actor = currentStaff();

        requireWorkable(order);
        if (!action.appliesTo(f.getFulfillmentType()) || f.getStatus() != action.getFrom()) {
            throw new FulfillmentConflictException(FulfillmentConflictException.STATUS_CHANGED,
                    "Trạng thái phần giao đã thay đổi hoặc không phù hợp với thao tác. Vui lòng tải lại trang.");
        }
        requireMethodMatchesType(order, f);

        String historyNote = action.getLabel();
        LocalDateTime now = gate.now().withNano(0);
        switch (action) {
            case PREPARE -> f.setPreparedAt(now);
            case PACK -> f.setPackedAt(now);
            case SHIP -> {
                String tracking = trackingCode(input);
                f.setShippedAt(now);
                if (tracking != null) {
                    f.setTrackingCode(tracking);
                    historyNote = historyNote + " · Mã vận đơn " + tracking;
                }
            }
            case MARK_READY -> {
                if (f.getPickupCode() != null || f.getReadyAt() != null) {
                    throw new FulfillmentConflictException(FulfillmentConflictException.INCONSISTENT_FULFILLMENT,
                            "Thông tin nhận hàng đã tồn tại không nhất quán. Vui lòng tải lại trang.");
                }
                f.setPickupCode(newPickupCode(f.getStore().getId()));
                f.setReadyAt(now);
            }
            case COMPLETE_DELIVERY -> {
                if (order.getPaymentMethod() == PaymentMethod.COD) {
                    payments.recordOfflineCollected(order, f); // before the status changes: it checks SHIPPING
                }
                f.setCompletedAt(now);
            }
            case COMPLETE_PICKUP -> {
                limiter.requireNotLocked(f.getId());
                try {
                    codes.verify(input, f.getPickupCode());
                } catch (BadRequestException wrongCode) {
                    limiter.recordFailure(f.getId());
                    throw wrongCode; // nothing has been written yet; payment is untouched
                }
                if (f.getReadyAt() == null || f.getPickedUpAt() != null) {
                    throw new FulfillmentConflictException(FulfillmentConflictException.INCONSISTENT_FULFILLMENT,
                            "Thông tin nhận hàng không nhất quán. Vui lòng tải lại trang.");
                }
                if (order.getPaymentMethod() == PaymentMethod.PAY_AT_STORE) {
                    payments.recordOfflineCollected(order, f);
                }
                f.setPickedUpAt(now);
                f.setCompletedAt(now);
                limiter.reset(f.getId());
            }
            case FAIL_DELIVERY, FAIL_PICKUP -> {
                String reason = reason(input);
                f.setNote(reason);
                historyNote = historyNote + " · " + reason;
            }
        }

        OrderFulfillmentStatusHistory entry = new OrderFulfillmentStatusHistory();
        entry.setFulfillment(f);
        entry.setOldStatus(f.getStatus());
        entry.setNewStatus(action.getTo());
        entry.setChangedBy(actor);
        entry.setNote(historyNote.length() > 500 ? historyNote.substring(0, 500) : historyNote);
        f.setStatus(action.getTo());
        fulfillments.saveAndFlush(f);
        histories.save(entry);
        aggregator.recompute(order, actor, "Tổng hợp từ phần giao #" + f.getId() + ": " + action.getLabel());
    }

    /**
     * The generic "hand over" step of the plan: completes a DELIVERY Fulfillment (SHIPPING → COMPLETED) or a
     * STORE_PICKUP Fulfillment (READY_FOR_PICKUP → COMPLETED, pickup code required). Same rules as the two explicit
     * completion actions; the type is read from the Fulfillment, never from the request.
     */
    @Transactional
    public void handover(Long fulfillmentId, String pickupCode) {
        if (fulfillmentId == null) {
            throw new ResourceNotFoundException("Không tìm thấy phần giao hàng.");
        }
        OrderFulfillmentRepository.FulfillmentKey key = requireInScope(fulfillmentId, scope.getAssignedStoreIds());
        if (key.getFulfillmentType() == FulfillmentType.STORE_PICKUP) {
            perform(fulfillmentId, FulfillmentAction.COMPLETE_PICKUP, pickupCode);
        } else {
            perform(fulfillmentId, FulfillmentAction.COMPLETE_DELIVERY, null);
        }
    }

    // ---------------------------------------------------------------------------------------------- helpers

    /** 404 for an unknown id, 403 for a Fulfillment of a Store the Staff is not assigned to. */
    private OrderFulfillmentRepository.FulfillmentKey requireInScope(Long fulfillmentId, Collection<Long> assigned) {
        if (fulfillmentId == null) {
            throw new ResourceNotFoundException("Không tìm thấy phần giao hàng.");
        }
        OrderFulfillmentRepository.FulfillmentKey key = fulfillments.findKeyById(fulfillmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phần giao hàng."));
        if (!assigned.contains(key.getStoreId())) {
            throw new AccessDeniedException("Bạn không được phân công xử lý phần giao hàng của chi nhánh này.");
        }
        return key;
    }

    private User currentStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return users.findByEmail(auth.getName())
                .filter(u -> u.isActive() && u.getRole().getName() == RoleName.STAFF)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản nhân viên không hợp lệ."));
    }

    /** The payment gate: the Order decides whether any of its Fulfillments may move (DEC-15, DEC-17, DEC-21). */
    private void requireWorkable(Order order) {
        String blocked = blockedCode(order);
        if (blocked != null) {
            throw new FulfillmentConflictException(blocked, blockedMessage(blocked));
        }
    }

    private String blockedCode(Order order) {
        OrderStatus status = order.getOrderStatus();
        if (status == OrderStatus.CANCELLED) {
            return FulfillmentConflictException.ORDER_CANCELLED;
        }
        if (status == OrderStatus.NEEDS_ATTENTION) {
            return FulfillmentConflictException.ORDER_NEEDS_ATTENTION;
        }
        if (status == OrderStatus.PENDING_PAYMENT) {
            return FulfillmentConflictException.PAYMENT_PENDING;
        }
        if (!gate.allowsFulfillmentWork(order)) {
            return FulfillmentConflictException.PAYMENT_NOT_VERIFIED;
        }
        return null;
    }

    private static String blockedMessage(String code) {
        return switch (code) {
            case FulfillmentConflictException.ORDER_CANCELLED -> "Đơn hàng đã bị hủy; không thể xử lý phần giao.";
            case FulfillmentConflictException.ORDER_NEEDS_ATTENTION ->
                    "Đơn hàng đang chờ quản trị viên xử lý ngoại lệ; chưa thể thao tác.";
            case FulfillmentConflictException.PAYMENT_PENDING ->
                    "Đơn hàng đang chờ thanh toán; chưa thể chuẩn bị hàng.";
            default -> "Thanh toán của đơn hàng chưa được xác nhận; chưa thể xử lý phần giao.";
        };
    }

    private static void requireMethodMatchesType(Order order, OrderFulfillment f) {
        PaymentMethod method = order.getPaymentMethod();
        boolean valid = f.getFulfillmentType() == FulfillmentType.DELIVERY
                ? method == PaymentMethod.COD || method == PaymentMethod.ONLINE
                : method == PaymentMethod.PAY_AT_STORE || method == PaymentMethod.ONLINE;
        if (!valid || order.getFulfillmentType() != f.getFulfillmentType()) {
            throw new FulfillmentConflictException(FulfillmentConflictException.INCONSISTENT_FULFILLMENT,
                    "Phương thức thanh toán không phù hợp với hình thức nhận hàng của phần giao.");
        }
    }

    private String newPickupCode(Long storeId) {
        for (int i = 0; i < CODE_ATTEMPTS; i++) {
            String candidate = codes.generate();
            if (!fulfillments.existsByStoreIdAndPickupCode(storeId, candidate)) {
                return candidate;
            }
        }
        throw new FulfillmentConflictException(FulfillmentConflictException.INCONSISTENT_FULFILLMENT,
                "Chưa tạo được mã nhận hàng. Vui lòng thử lại.");
    }

    private static String trackingCode(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String value = input.strip();
        if (!TRACKING.matcher(value).matches()) {
            throw new BadRequestException("Mã vận đơn chỉ gồm 3-50 ký tự chữ, số, '.', '_' hoặc '-'.");
        }
        return value;
    }

    private static String reason(String input) {
        String value = input == null ? "" : input.strip();
        if (value.length() < MIN_REASON || value.length() > MAX_REASON) {
            throw new BadRequestException("Vui lòng nhập lý do từ " + MIN_REASON + " đến " + MAX_REASON + " ký tự.");
        }
        return value;
    }

    private Page<FulfillmentQueueItemResponse> toQueueItems(Page<OrderFulfillment> rows) {
        List<Long> ids = rows.getContent().stream().map(OrderFulfillment::getId).toList();
        Map<Long, List<OrderItem>> byPart = ids.isEmpty() ? Map.of() : items.findByFulfillmentIdInOrderByIdAsc(ids)
                .stream().collect(Collectors.groupingBy(i -> i.getFulfillment().getId(), LinkedHashMap::new,
                        Collectors.toList()));
        return rows.map(f -> toItem(f, byPart.getOrDefault(f.getId(), List.of())));
    }

    private FulfillmentQueueItemResponse toItem(OrderFulfillment f, List<OrderItem> lines) {
        Order order = f.getOrder();
        boolean open = OPEN_STATUSES.contains(f.getStatus());
        String blockedCode = open ? blockedCode(order) : null;
        List<FulfillmentAction> actions = !open || blockedCode != null ? List.<FulfillmentAction>of()
                : Arrays.stream(FulfillmentAction.values())
                        .filter(a -> a.appliesTo(f.getFulfillmentType()) && a.getFrom() == f.getStatus()).toList();
        return new FulfillmentQueueItemResponse(f.getId(), order.getId(), f.getStore().getId(),
                f.getStore().getName(), f.getFulfillmentType(), f.getStatus(), order.getOrderStatus(),
                order.getPaymentMethod(), order.getPaymentStatus(), order.getUser().getFullName(),
                order.getReceiverName(), lines.size(), lines.stream().mapToInt(OrderItem::getQuantity).sum(),
                f.getAssignedAt(), actions, blockedCode == null ? null : blockedMessage(blockedCode));
    }

    /** Open statuses, for counters and tests. */
    public static Set<OrderFulfillmentStatus> openStatuses() {
        return Set.copyOf(OPEN_STATUSES);
    }

    /** First day of a date range filter given as text (invalid text means "no bound"). */
    public static LocalDate parseDate(String text) {
        try {
            if (text == null || text.isBlank()) return null;
            LocalDate date = LocalDate.parse(text.strip());
            // Keep both JDBC bounds representable, including to.plusDays(1); invalid text already means no bound.
            return date.isBefore(MIN_TIME.toLocalDate()) || !date.isBefore(MAX_TIME.toLocalDate()) ? null : date;
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }
}
