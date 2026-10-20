package com.oneshop.service.fulfillment;

import com.oneshop.dto.response.FulfillmentExceptionResponse;
import com.oneshop.dto.response.FulfillmentHistoryResponse;
import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.Order;
import com.oneshop.entity.OrderFulfillment;
import com.oneshop.entity.OrderFulfillmentStatus;
import com.oneshop.entity.OrderFulfillmentStatusHistory;
import com.oneshop.entity.OrderItem;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.PaymentStatus;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.FulfillmentConflictException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.OrderFulfillmentRepository;
import com.oneshop.repository.OrderFulfillmentStatusHistoryRepository;
import com.oneshop.repository.OrderItemRepository;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.PaymentRefundRepository;
import com.oneshop.repository.PaymentRepository;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.InventoryService;
import com.oneshop.service.payment.OrderStatusWriter;
import com.oneshop.service.payment.PartialRefundService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OPEN-10 / OPEN-10.1: how an Administrator closes a failed delivery. Staff only report the failure (Fulfillment FAILED,
 * Order NEEDS_ATTENTION, nothing else moves; the hold on the Order stays until every failed part has a decision). Each
 * decision applies to ONE Fulfillment, has a written reason and records the Administrator:
 *
 * <ul>
 *   <li><b>Redeliver</b> ({@code FAILED → PACKED} for DELIVERY, {@code FAILED → READY_FOR_PICKUP} for STORE_PICKUP): the
 *       goods are at the Store and can be handed over again. Nothing is deducted, restored or refunded; no Payment or
 *       Order is created.</li>
 *   <li><b>Confirm undeliverable</b> ({@code FAILED → CANCELLED}) with the state of the goods:
 *       <i>returned</i> puts the lines of this Fulfillment back into its Store's stock once; <i>lost</i> leaves stock
 *       alone; <i>unconfirmed</i> is NOT a decision: the exception stays open (the Fulfillment stays FAILED, the Order
 *       stays NEEDS_ATTENTION) and only a note is recorded until the goods are known.</li>
 * </ul>
 *
 * <p><b>Money (ONLINE, collected).</b> Confirming a part undeliverable refunds, in simulation and through the refund
 * ledger, the value of ITS goods; the one shipping fee is refunded only when nothing at all is delivered. See
 * {@link PartialRefundService}. COD and pay-at-store collected nothing for an undelivered part, so there is nothing to
 * refund (such an Order has a single Fulfillment).
 *
 * <p><b>The Order status is never edited.</b> When no Fulfillment is FAILED any more it is re-derived from the
 * Fulfillments with the aggregation rule: every live part COMPLETED gives COMPLETED (with some parts CANCELLED as
 * undeliverable the Order is "completed with undelivered parts"; a CANCELLED part is not work left), all parts CANCELLED
 * gives CANCELLED, otherwise the Order is still being worked on (PROCESSING, SHIPPING, PARTIALLY_COMPLETED, CONFIRMED).
 * COMPLETED Fulfillments are never touched.
 *
 * <p>Concurrency and idempotency: the Order is locked first, then its Fulfillments (ascending id), then StoreProducts
 * (ascending id), the order every other writer uses. The first request moves the Fulfillment out of FAILED inside the
 * same transaction as every side effect; a repeated or concurrent request finds it no longer FAILED and changes nothing.
 * The database additionally refuses a second GOODS / SHIPPING refund.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminFulfillmentExceptionService {

    public static final String ALREADY_RESOLVED = "FAILURE_ALREADY_RESOLVED";
    public static final String PARTIAL_SETTLEMENT_REQUIRED = "PARTIAL_SETTLEMENT_REQUIRED";
    public static final String REDELIVERY_NOT_POSSIBLE = "REDELIVERY_NOT_POSSIBLE";
    public static final String NOT_A_DELIVERY_FAILURE = "NOT_A_DELIVERY_FAILURE";

    public static final int REASON_MIN = 5;
    public static final int REASON_MAX = 400; // the history note is 500 characters including the actor

    /** Prefix of {@code cancel_reason} of an undeliverable part: the customer-safe cause shown to the customer. */
    public static final String UNDELIVERABLE_PREFIX = "Không giao được: ";

    /** The state of the goods an Administrator records when a delivery cannot be completed. */
    public enum GoodsStatus {
        /** back at the Store and fit to sell: the lines go back into its stock */
        RETURNED("Hàng đã về chi nhánh"),
        /** lost / not recoverable: stock is not changed */
        LOST("Hàng thất lạc"),
        /** cannot be confirmed yet: the exception stays open, nothing is decided, nothing changes */
        UNCONFIRMED("Chưa xác nhận được tình trạng hàng");

        private final String label;

        GoodsStatus(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public static GoodsStatus fromKey(String key) {
            if (key == null) {
                return null;
            }
            for (GoodsStatus status : values()) {
                if (status.name().equalsIgnoreCase(key.trim())) {
                    return status;
                }
            }
            return null;
        }
    }

    public enum Kind { REDELIVERY_OPENED, UNDELIVERABLE_RECORDED, AWAITING_VERIFICATION }

    /**
     * @param orderStatus      the Order status after the decision
     * @param orderClosed      true when the Order ended CANCELLED (nothing was delivered)
     * @param partiallyClosed  true when the Order ended COMPLETED while some part was confirmed undeliverable
     * @param stockRestored    true when this decision put stock back
     * @param refund           the simulated refund made by this decision, or {@code null} when none applies
     * @param failedLeft       Fulfillments still FAILED and waiting for a decision
     */
    public record Outcome(Kind kind, OrderStatus orderStatus, boolean orderClosed, boolean partiallyClosed,
                          boolean stockRestored, PartialRefundService.Result refund, int failedLeft) {
    }

    private final OrderRepository orders;
    private final OrderFulfillmentRepository fulfillments;
    private final OrderFulfillmentStatusHistoryRepository histories;
    private final OrderItemRepository items;
    private final PaymentRepository payments;
    private final PaymentRefundRepository refundLedger;
    private final InventoryService inventory;
    private final PartialRefundService refunds;
    private final OrderStatusWriter writer;
    private final UserRepository users;

    public AdminFulfillmentExceptionService(OrderRepository orders, OrderFulfillmentRepository fulfillments,
                                            OrderFulfillmentStatusHistoryRepository histories, OrderItemRepository items,
                                            PaymentRepository payments, PaymentRefundRepository refundLedger,
                                            InventoryService inventory, PartialRefundService refunds,
                                            OrderStatusWriter writer, UserRepository users) {
        this.orders = orders;
        this.fulfillments = fulfillments;
        this.histories = histories;
        this.items = items;
        this.payments = payments;
        this.refundLedger = refundLedger;
        this.inventory = inventory;
        this.refunds = refunds;
        this.writer = writer;
        this.users = users;
    }

    // ------------------------------------------------------------------ read

    /** {@code null} when the Order has no failed delivery waiting for a decision. */
    @Transactional(readOnly = true)
    public FulfillmentExceptionResponse getException(Long orderId) {
        Order order = orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        List<OrderFulfillment> parts = fulfillments.findByOrderIdOrderByAssignedAtAscIdAsc(orderId);
        if (order.getOrderStatus() != OrderStatus.NEEDS_ATTENTION
                || parts.stream().noneMatch(p -> p.getStatus() == OrderFulfillmentStatus.FAILED)) {
            return null;
        }
        Map<Long, List<OrderItem>> linesByPart = items.findByFulfillmentIdInOrderByIdAsc(
                parts.stream().map(OrderFulfillment::getId).toList()).stream()
                .collect(Collectors.groupingBy(i -> i.getFulfillment().getId()));
        List<Payment> rows = payments.findByOrderIdOrderByIdAsc(orderId);
        FulfillmentExceptionResponse.Money money = money(order, parts, linesByPart, rows);
        List<FulfillmentExceptionResponse.Part> failed = new ArrayList<>();
        for (OrderFulfillment part : parts) {
            if (part.getStatus() != OrderFulfillmentStatus.FAILED) {
                continue;
            }
            List<OrderItem> lines = linesByPart.getOrDefault(part.getId(), List.of());
            String redeliverBlocked = redeliverBlocked(order, part);
            String undeliverableBlocked = undeliverableBlocked(order, part, parts);
            List<OrderFulfillmentStatusHistory> history = histories.findByFulfillmentIdOrderByChangedAtAscIdAsc(part.getId());
            OrderFulfillmentStatusHistory failure = history.stream()
                    .filter(h -> h.getNewStatus() == OrderFulfillmentStatus.FAILED && h.getOldStatus() != OrderFulfillmentStatus.FAILED)
                    .reduce((a, b) -> b).orElse(null);
            String goods = lastGoodsStatus(history);
            PartialRefundService.Preview preview = order.getPaymentMethod() == PaymentMethod.ONLINE
                    && order.getPaymentStatus() == OrderPaymentStatus.PAID
                    ? refunds.preview(order, part, parts) : new PartialRefundService.Preview(BigDecimal.ZERO, BigDecimal.ZERO);
            failed.add(new FulfillmentExceptionResponse.Part(part.getId(), part.getStore().getId(),
                    part.getStore().getName(), part.getStore().getStatus() == ActiveStatus.ACTIVE,
                    part.getFulfillmentType(), failureReason(failure), failure == null ? null : failure.getChangedAt(),
                    failure == null || failure.getChangedBy() == null ? null : failure.getChangedBy().getFullName(),
                    goods, GoodsStatus.UNCONFIRMED.name().equals(goods),
                    lines.stream().map(i -> new FulfillmentExceptionResponse.Line(i.getProductName(), i.getQuantity(),
                            i.getSubtotal(), i.getStoreProduct().getQuantity())).toList(),
                    sum(lines), preview.goods(), preview.shipping(),
                    history.stream().map(h -> new FulfillmentHistoryResponse(h.getOldStatus(),
                            h.getNewStatus(), h.getChangedBy() == null ? "Hệ thống" : h.getChangedBy().getFullName(),
                            h.getNote(), h.getChangedAt())).toList(),
                    redeliverBlocked == null, redeliverBlocked, undeliverableBlocked == null, undeliverableBlocked));
        }
        return new FulfillmentExceptionResponse(orderId, order.getOrderStatus(), order.getPaymentMethod(),
                order.getPaymentStatus(), money, failed);
    }

    // ------------------------------------------------------------------ decision A: redeliver

    /**
     * @param goodsAtStore the Administrator confirms the goods are at the Store and fit to be handed over again
     * @param reason       why the delivery may be tried again ({@value #REASON_MIN} to {@value #REASON_MAX} characters)
     */
    @Transactional
    public Outcome redeliver(Long orderId, Long fulfillmentId, boolean goodsAtStore, String reason) {
        User admin = currentAdmin();
        String why = reason(reason);
        Locked locked = lock(orderId, fulfillmentId);
        Order order = locked.order();
        OrderFulfillment part = locked.part();
        requireFailed(order, part);
        if (!goodsAtStore) {
            throw new BadRequestException("goodsAtStore", "Vui lòng xác nhận hàng đang ở chi nhánh và đủ điều kiện giao lại.");
        }
        String blocked = redeliverBlocked(order, part);
        if (blocked != null) {
            throw new FulfillmentConflictException(REDELIVERY_NOT_POSSIBLE, blocked);
        }

        OrderFulfillmentStatus target = part.getFulfillmentType() == FulfillmentType.DELIVERY
                ? OrderFulfillmentStatus.PACKED : OrderFulfillmentStatus.READY_FOR_PICKUP;
        String note = OrderStatusWriter.abbreviate("[ADMIN_REDELIVER] " + admin.getFullName() + ": " + why);
        if (target == OrderFulfillmentStatus.PACKED) {
            // the parcel is back and packed; Staff hand it to the carrier again (a new tracking code, a new SHIPPING time)
            part.setShippedAt(null);
            part.setTrackingCode(null);
        }
        part.setNote(note);
        history(part, target, admin, note);
        part.setStatus(target);
        fulfillments.saveAndFlush(part);

        OrderStatus status = settle(order, locked.parts(), admin,
                "[ADMIN_REDELIVER] Phần giao #" + part.getId() + " được giao lại: " + why);
        return new Outcome(Kind.REDELIVERY_OPENED, status, false, false, false, null, failedLeft(locked.parts()));
    }

    // ------------------------------------------------------------------ decision B: undeliverable

    @Transactional
    public Outcome markUndeliverable(Long orderId, Long fulfillmentId, GoodsStatus goods, String reason) {
        User admin = currentAdmin();
        String why = reason(reason);
        if (goods == null) {
            throw new BadRequestException("goods", "Vui lòng chọn tình trạng hàng hóa.");
        }
        Locked locked = lock(orderId, fulfillmentId);
        Order order = locked.order();
        OrderFulfillment part = locked.part();
        requireFailed(order, part);

        if (goods == GoodsStatus.UNCONFIRMED) {
            // not a decision: the goods are not known yet, so nothing is cancelled, restored or refunded
            String note = OrderStatusWriter.abbreviate("[ADMIN_PENDING_VERIFICATION][GOODS:" + goods.name() + "] "
                    + admin.getFullName() + ": " + why);
            history(part, OrderFulfillmentStatus.FAILED, admin, note);
            fulfillments.flush();
            writer.audit(order, admin, "[ADMIN_PENDING_VERIFICATION] Phần giao #" + part.getId()
                    + " chờ xác minh tình trạng hàng: " + why);
            return new Outcome(Kind.AWAITING_VERIFICATION, order.getOrderStatus(), false, false, false, null,
                    failedLeft(locked.parts()));
        }

        String blocked = undeliverableBlocked(order, part, locked.parts());
        if (blocked != null) {
            throw new FulfillmentConflictException(PARTIAL_SETTLEMENT_REQUIRED, blocked);
        }

        String note = OrderStatusWriter.abbreviate("[ADMIN_UNDELIVERABLE][GOODS:" + goods.name() + "] "
                + admin.getFullName() + " · " + goods.label() + ": " + why);
        boolean restored = false;
        if (goods == GoodsStatus.RETURNED) {
            // inside this transaction the Fulfillment leaves FAILED right below, so this can run at most once
            inventory.restoreForUndeliverableFulfillment(order, part, OrderStatusWriter.abbreviate(
                    "Hoàn tồn phần giao #" + part.getId() + " không giao được (hàng đã về chi nhánh) - Order #" + order.getId()));
            restored = true;
        }
        LocalDateTime now = LocalDateTime.now().withNano(0);
        history(part, OrderFulfillmentStatus.CANCELLED, admin, note);
        part.setStatus(OrderFulfillmentStatus.CANCELLED);
        part.setCancelledAt(now);
        part.setCancelReason(OrderStatusWriter.abbreviate(UNDELIVERABLE_PREFIX + why));
        part.setNote(note);
        fulfillments.saveAndFlush(part);

        PartialRefundService.Result refund = null;
        if (order.getPaymentMethod() == PaymentMethod.ONLINE && order.getPaymentStatus() == OrderPaymentStatus.PAID) {
            refund = refunds.refundUndeliverable(order, part, locked.parts(), admin,
                    "Hoàn tiền mô phỏng: phần giao #" + part.getId() + " không giao được. " + why);
        }

        int failedLeft = failedLeft(locked.parts());
        String orderNote = "[ADMIN_UNDELIVERABLE] Phần giao #" + part.getId() + " không giao được (" + goods.label() + "): "
                + why + (refund == null || refund.status() == PartialRefundService.Status.NOT_APPLICABLE ? ""
                : " · hoàn tiền mô phỏng " + refund.total().toPlainString());
        OrderStatus status = settle(order, locked.parts(), admin, orderNote);
        boolean closed = status == OrderStatus.CANCELLED && failedLeft == 0;
        boolean partially = status == OrderStatus.COMPLETED && failedLeft == 0;
        return new Outcome(Kind.UNDELIVERABLE_RECORDED, status, closed, partially, restored, refund, failedLeft);
    }

    // ------------------------------------------------------------------ rules

    /** {@code null} when decision A is open for this FAILED Fulfillment. */
    private String redeliverBlocked(Order order, OrderFulfillment part) {
        if (part.getStore().getStatus() != ActiveStatus.ACTIVE) {
            return "Chi nhánh đang ngừng hoạt động nên không thể giao lại.";
        }
        if (part.getFulfillmentType() == FulfillmentType.DELIVERY && !part.getStore().isDeliveryEnabled()) {
            return "Chi nhánh hiện không hỗ trợ giao hàng tận nơi.";
        }
        if (part.getFulfillmentType() == FulfillmentType.STORE_PICKUP) {
            if (!part.getStore().isPickupEnabled()) {
                return "Chi nhánh hiện không hỗ trợ nhận tại cửa hàng.";
            }
            if (part.getPickupCode() == null || part.getReadyAt() == null) {
                return "Thiếu thông tin nhận hàng của phần giao này; không thể đặt lại trạng thái sẵn sàng.";
            }
        }
        if (order.getPaymentMethod() == PaymentMethod.ONLINE ? order.getPaymentStatus() != OrderPaymentStatus.PAID
                : order.getPaymentStatus() != OrderPaymentStatus.UNPAID) {
            return "Trạng thái thanh toán của đơn không cho phép giao lại. Vui lòng kiểm tra thanh toán.";
        }
        return null;
    }

    /**
     * {@code null} when decision B is open. ONLINE collected money is refunded by part (simulation), so other parts may
     * be delivered, in progress or failed. COD / pay-at-store collected nothing and have one part: nothing else may be
     * live, otherwise the amount still to collect would have to change, which V3 does not define.
     */
    private String undeliverableBlocked(Order order, OrderFulfillment part, List<OrderFulfillment> parts) {
        if (order.getPaymentMethod() == PaymentMethod.ONLINE) {
            if (order.getPaymentStatus() != OrderPaymentStatus.PAID) {
                return "Đơn ONLINE chưa ở trạng thái đã thanh toán (hoặc đã hoàn đủ); xử lý ở mục thanh toán.";
            }
            return null;
        }
        boolean othersSettled = parts.stream().filter(p -> !p.getId().equals(part.getId()))
                .allMatch(p -> p.getStatus() == OrderFulfillmentStatus.CANCELLED || p.getStatus() == OrderFulfillmentStatus.FAILED);
        if (!othersSettled) {
            return "Đơn thu tiền khi nhận hàng còn phần giao khác đang thực hiện; điều chỉnh số tiền phải thu chưa được đặc tả. "
                    + "Có thể giao lại phần này.";
        }
        if (order.getPaymentStatus() != OrderPaymentStatus.UNPAID) {
            return "Đơn đã ghi nhận thu tiền; cần chủ dự án quyết định cách đối soát trước khi xác nhận không giao được.";
        }
        return null;
    }

    // ------------------------------------------------------------------ helpers

    private record Locked(Order order, OrderFulfillment part, List<OrderFulfillment> parts) {
    }

    /** Order first, then all its Fulfillments in ascending id: the lock order of every other writer. */
    private Locked lock(Long orderId, Long fulfillmentId) {
        if (orderId == null || fulfillmentId == null) {
            throw new ResourceNotFoundException("Không tìm thấy phần giao hàng.");
        }
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        List<OrderFulfillment> parts = fulfillments.findByOrderIdForUpdate(orderId);
        OrderFulfillment part = parts.stream().filter(p -> p.getId().equals(fulfillmentId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Phần giao không thuộc đơn hàng này."));
        return new Locked(order, part, parts);
    }

    /** The decision is only for a delivery failure that is still waiting; a repeated request lands here and changes nothing. */
    private static void requireFailed(Order order, OrderFulfillment part) {
        if (part.getStatus() != OrderFulfillmentStatus.FAILED) {
            throw new FulfillmentConflictException(ALREADY_RESOLVED,
                    "Phần giao này không còn ở trạng thái thất bại (có thể đã được xử lý). Vui lòng tải lại trang.");
        }
        if (order.getOrderStatus() != OrderStatus.NEEDS_ATTENTION) {
            throw new FulfillmentConflictException(NOT_A_DELIVERY_FAILURE,
                    "Đơn hàng không ở trạng thái cần xử lý ngoại lệ giao hàng.");
        }
    }

    private void history(OrderFulfillment part, OrderFulfillmentStatus to, User admin, String note) {
        OrderFulfillmentStatusHistory entry = new OrderFulfillmentStatusHistory();
        entry.setFulfillment(part);
        entry.setOldStatus(part.getStatus());
        entry.setNewStatus(to);
        entry.setChangedBy(admin);
        entry.setNote(OrderStatusWriter.abbreviate(note));
        histories.save(entry);
    }

    /**
     * Re-derives the Order status from its Fulfillments (the in-memory statuses are already the new ones). While another
     * part is still FAILED the Order stays NEEDS_ATTENTION (the hold stays) and only an audit row is written.
     */
    private OrderStatus settle(Order order, List<OrderFulfillment> parts, User admin, String note) {
        if (failedLeft(parts) > 0) {
            writer.audit(order, admin, note + "; còn phần giao thất bại chờ xử lý");
            return order.getOrderStatus();
        }
        OrderStatus target = OrderStatusAggregator.derive(parts.stream().map(OrderFulfillment::getStatus).toList(),
                order.getPaymentStatus() == OrderPaymentStatus.PAID);
        if (target == null || target == OrderStatus.NEEDS_ATTENTION) {
            writer.audit(order, admin, note + "; trạng thái đơn giữ nguyên");
            return order.getOrderStatus();
        }
        writer.writeResolved(order, target, admin, note);
        return target;
    }

    private static int failedLeft(List<OrderFulfillment> parts) {
        return (int) parts.stream().filter(p -> p.getStatus() == OrderFulfillmentStatus.FAILED).count();
    }

    private FulfillmentExceptionResponse.Money money(Order order, List<OrderFulfillment> parts,
                                                    Map<Long, List<OrderItem>> lines, List<Payment> rows) {
        BigDecimal delivered = BigDecimal.ZERO;
        BigDecimal undelivered = BigDecimal.ZERO;
        BigDecimal inProgress = BigDecimal.ZERO;
        for (OrderFulfillment part : parts) {
            BigDecimal value = sum(lines.getOrDefault(part.getId(), List.of()));
            switch (part.getStatus()) {
                case COMPLETED -> delivered = delivered.add(value);
                case FAILED -> undelivered = undelivered.add(value);
                case CANCELLED -> { /* neither delivered nor owed */ }
                default -> inProgress = inProgress.add(value);
            }
        }
        BigDecimal collected = order.getPaymentMethod() == PaymentMethod.ONLINE
                ? rows.stream().filter(p -> p.getMethod() == PaymentMethod.ONLINE && p.getStatus() == PaymentStatus.SUCCESS)
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                : order.getPaymentStatus() == OrderPaymentStatus.PAID ? order.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal refunded = rows.stream().filter(p -> p.getMethod() == PaymentMethod.ONLINE && p.getStatus() == PaymentStatus.SUCCESS)
                .map(p -> refundLedger.sumCommitted(p.getId())).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FulfillmentExceptionResponse.Money(order.getTotalAmount(), collected, refunded,
                collected.subtract(refunded), delivered, undelivered, inProgress);
    }

    /** The reason Staff wrote: the note of the history row that moved the part to FAILED ("label · reason"). */
    private static String failureReason(OrderFulfillmentStatusHistory failure) {
        if (failure == null || failure.getNote() == null) {
            return null;
        }
        String note = failure.getNote();
        int dot = note.indexOf(" · ");
        return dot < 0 ? note : note.substring(dot + 3);
    }

    /** The last goods status an Administrator recorded for this part, from its history, or {@code null}. */
    private static String lastGoodsStatus(List<OrderFulfillmentStatusHistory> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            String note = history.get(i).getNote();
            int at = note == null ? -1 : note.indexOf("[GOODS:");
            if (at >= 0) {
                int end = note.indexOf(']', at);
                if (end > at) {
                    return note.substring(at + 7, end);
                }
            }
        }
        return null;
    }

    private static BigDecimal sum(List<OrderItem> lines) {
        return lines.stream().map(OrderItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String reason(String reason) {
        String text = reason == null ? "" : reason.trim();
        if (text.length() < REASON_MIN || text.length() > REASON_MAX) {
            throw new BadRequestException("reason", "Vui lòng nhập lý do xử lý từ " + REASON_MIN + " đến " + REASON_MAX + " ký tự.");
        }
        return text;
    }

    private User currentAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new AccessDeniedException("Chỉ quản trị viên được xử lý ngoại lệ giao hàng.");
        }
        return users.findByEmail(auth.getName())
                .filter(u -> u.isActive() && u.getRole().getName() == RoleName.ADMIN)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản quản trị không hợp lệ."));
    }
}
