package com.oneshop.service.returns;

import com.oneshop.dto.response.OrderReturnResponse;
import com.oneshop.dto.response.ReturnEligibilityResponse;
import com.oneshop.entity.Order;
import com.oneshop.entity.OrderFulfillment;
import com.oneshop.entity.OrderFulfillmentStatus;
import com.oneshop.entity.OrderItem;
import com.oneshop.entity.OrderReturn;
import com.oneshop.entity.OrderReturnImage;
import com.oneshop.entity.OrderReturnItem;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentStatus;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.OrderFulfillmentRepository;
import com.oneshop.repository.OrderItemRepository;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.OrderReturnImageRepository;
import com.oneshop.repository.OrderReturnItemRepository;
import com.oneshop.repository.OrderReturnRepository;
import com.oneshop.repository.PaymentRefundRepository;
import com.oneshop.repository.PaymentRepository;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.image.ImageTransactionCoordinator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The Customer side of a post-delivery return: the whole completed Order is given back, within {@link #RETURN_WINDOW}
 * of the moment its latest Fulfillment was completed, with a reason and photos. Nothing moves yet: the request waits
 * for an Administrator ({@link AdminOrderReturnService}). The Order and Fulfillment statuses are never changed.
 *
 * <p>Only the lines of COMPLETED Fulfillments are returned (a part confirmed undeliverable was refunded earlier and
 * never reached the Customer). The refund is the goods value of those lines; the shipping fee is kept.
 */
@Service
@Transactional(readOnly = true)
public class CustomerOrderReturnService {

    /** Time the Customer has, counted from the completion of the latest Fulfillment. */
    public static final Duration RETURN_WINDOW = Duration.ofDays(3);
    public static final int MIN_REASON = 5;
    public static final int MAX_REASON = 500;
    public static final int MAX_IMAGES = 3;
    static final String IMAGE_FOLDER = "returns";

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** What a request would return, computed from the current data. */
    private record Plan(ReturnEligibilityResponse eligibility, List<OrderItem> lines, LocalDateTime receivedAt) {
    }

    private final OrderRepository orders;
    private final UserRepository users;
    private final OrderFulfillmentRepository fulfillments;
    private final OrderItemRepository orderItems;
    private final PaymentRepository payments;
    private final PaymentRefundRepository refunds;
    private final OrderReturnRepository returns;
    private final OrderReturnItemRepository returnItems;
    private final OrderReturnImageRepository returnImages;
    private final ImageTransactionCoordinator imageCoordinator;
    private final Clock clock;

    public CustomerOrderReturnService(OrderRepository orders, UserRepository users,
                                      OrderFulfillmentRepository fulfillments, OrderItemRepository orderItems,
                                      PaymentRepository payments, PaymentRefundRepository refunds,
                                      OrderReturnRepository returns, OrderReturnItemRepository returnItems,
                                      OrderReturnImageRepository returnImages,
                                      ImageTransactionCoordinator imageCoordinator, Clock clock) {
        this.orders = orders;
        this.users = users;
        this.fulfillments = fulfillments;
        this.orderItems = orderItems;
        this.payments = payments;
        this.refunds = refunds;
        this.returns = returns;
        this.returnItems = returnItems;
        this.returnImages = returnImages;
        this.imageCoordinator = imageCoordinator;
        this.clock = clock;
    }

    /** The Customer's own return of one Order, if any. Another Customer's Order is "not found". */
    @PreAuthorize("hasRole('CUSTOMER') and #email == authentication.name")
    public Optional<OrderReturnResponse> find(String email, Long orderId) {
        User customer = requireCustomer(email);
        return returns.findByOrderIdAndUserId(orderId, customer.getId()).map(r -> OrderReturnMapper.toResponse(r,
                returnItems.findByOrderReturnIdOrderByIdAsc(r.getId()),
                returnImages.findByOrderReturnIdOrderBySortOrderAscIdAsc(r.getId()), false));
    }

    /** May this Customer ask to return this Order right now? Read only; the request itself re-checks under the lock. */
    @PreAuthorize("hasRole('CUSTOMER') and #email == authentication.name")
    public ReturnEligibilityResponse eligibility(String email, Long orderId) {
        User customer = requireCustomer(email);
        Order order = orders.findByIdAndUserEmail(orderId, email)
                .filter(o -> o.getUser().getId().equals(customer.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn."));
        return plan(order).eligibility();
    }

    /**
     * Records the request. Under the Order write lock (the lock every payment / fulfillment / return writer takes
     * first) the rules are checked again, the lines of the completed parts are copied and the photos are stored.
     *
     * @throws BadRequestException when the reason or the photos are invalid, or the Order cannot be returned now
     */
    @Transactional
    @PreAuthorize("hasRole('CUSTOMER') and #email == authentication.name")
    public OrderReturnResponse request(String email, Long orderId, String reason, List<MultipartFile> files) {
        User customer = requireCustomer(email);
        String cleanReason = cleanReason(reason);
        List<MultipartFile> photos = photos(files);

        Order order = orders.findByIdForUpdate(orderId)
                .filter(o -> o.getUser().getId().equals(customer.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn."));
        Plan plan = plan(order);
        if (!plan.eligibility().allowed()) {
            throw new BadRequestException(plan.eligibility().message());
        }

        OrderReturn request = new OrderReturn();
        request.setOrder(order);
        request.setUser(customer);
        request.setReason(cleanReason);
        request.setReceivedAt(plan.receivedAt());
        request.setRefundAmount(plan.eligibility().refundAmount());
        request.setRequestedAt(LocalDateTime.now(clock).withNano(0));
        returns.saveAndFlush(request);

        List<OrderReturnItem> items = new ArrayList<>();
        for (OrderItem line : plan.lines()) {
            OrderReturnItem item = new OrderReturnItem();
            item.setOrderReturn(request);
            item.setOrderItem(line);
            item.setFulfillment(line.getFulfillment());
            item.setStore(line.getFulfillment().getStore());
            item.setStoreProduct(line.getStoreProduct());
            item.setProductName(line.getProductName());
            item.setQuantity(line.getQuantity());
            item.setUnitPrice(line.getUnitPrice());
            item.setSubtotal(line.getSubtotal());
            items.add(returnItems.save(item));
        }
        List<OrderReturnImage> images = new ArrayList<>();
        int order0 = 0;
        for (MultipartFile photo : photos) {
            var uploaded = imageCoordinator.upload(photo, IMAGE_FOLDER);
            OrderReturnImage image = new OrderReturnImage();
            image.setOrderReturn(request);
            image.setImageUrl(uploaded.url());
            image.setPublicId(uploaded.publicId());
            image.setSortOrder(order0++);
            images.add(returnImages.save(image));
        }
        returnItems.flush();
        return OrderReturnMapper.toResponse(request, items, images, false);
    }

    // ------------------------------------------------------------------ rules

    private Plan plan(Order order) {
        if (returns.existsByOrderId(order.getId())) {
            return blocked("Đơn hàng này đã có yêu cầu trả hàng.");
        }
        if (order.getOrderStatus() != OrderStatus.COMPLETED) {
            return blocked("Chỉ đơn hàng đã hoàn tất (đã nhận hàng) mới được trả hàng.");
        }
        List<OrderFulfillment> parts = fulfillments.findByOrderIdOrderByAssignedAtAscIdAsc(order.getId());
        if (parts.stream().anyMatch(p -> p.getStatus() != OrderFulfillmentStatus.COMPLETED
                && p.getStatus() != OrderFulfillmentStatus.CANCELLED)) {
            return blocked("Đơn hàng còn phần giao chưa hoàn tất nên chưa thể trả hàng.");
        }
        List<OrderFulfillment> delivered = parts.stream().filter(p -> p.getStatus() == OrderFulfillmentStatus.COMPLETED)
                .toList();
        if (delivered.isEmpty() || delivered.stream().anyMatch(p -> p.getCompletedAt() == null)) {
            return blocked("Chưa xác định được thời điểm nhận hàng của đơn hàng này.");
        }
        LocalDateTime receivedAt = delivered.stream().map(OrderFulfillment::getCompletedAt)
                .max(Comparator.naturalOrder()).orElseThrow();
        LocalDateTime deadline = receivedAt.plus(RETURN_WINDOW);
        if (LocalDateTime.now(clock).isAfter(deadline)) {
            // receivedAt / deadline are kept so the order page can still explain why the window is closed
            return new Plan(new ReturnEligibilityResponse(false, "Đã quá " + RETURN_WINDOW.toDays()
                    + " ngày kể từ khi bạn nhận hàng (hạn trả hàng là " + deadline.format(WHEN) + ").",
                    receivedAt, deadline, null), List.of(), receivedAt);
        }
        List<OrderItem> lines = orderItems.findByOrderId(order.getId()).stream()
                .filter(i -> i.getFulfillment() != null && i.getFulfillment().getStatus() == OrderFulfillmentStatus.COMPLETED)
                .sorted(Comparator.comparing(OrderItem::getId)).toList();
        BigDecimal amount = lines.stream().map(OrderItem::getNetSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (lines.isEmpty()) {
            return blocked("Đơn hàng không có sản phẩm nào đã nhận để trả.");
        }
        if (amount.signum() <= 0) {
            return blocked("Tiền hàng của đơn đã được khuyến mãi giảm hết nên không có khoản nào để hoàn lại.");
        }
        List<Payment> collected = payments.findByOrderIdOrderByIdAsc(order.getId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS).toList();
        if (collected.size() != 1) {
            return blocked("Đơn hàng chưa có khoản thanh toán thành công để hoàn tiền.");
        }
        Payment payment = collected.get(0);
        if (amount.compareTo(payment.getAmount().subtract(refunds.sumCommitted(payment.getId()))) > 0) {
            return blocked("Số tiền còn có thể hoàn của đơn hàng không đủ để trả hàng.");
        }
        return new Plan(new ReturnEligibilityResponse(true, null, receivedAt, deadline, amount), lines, receivedAt);
    }

    private static Plan blocked(String message) {
        return new Plan(ReturnEligibilityResponse.blocked(message), List.of(), null);
    }

    private static String cleanReason(String reason) {
        String clean = reason == null ? "" : reason.trim();
        if (clean.length() < MIN_REASON || clean.length() > MAX_REASON) {
            throw new BadRequestException("reason", "Vui lòng nhập lý do trả hàng từ " + MIN_REASON + " đến "
                    + MAX_REASON + " ký tự.");
        }
        return clean;
    }

    private static List<MultipartFile> photos(List<MultipartFile> files) {
        List<MultipartFile> given = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (given.isEmpty() || given.size() > MAX_IMAGES) {
            throw new BadRequestException("images", "Vui lòng đính kèm từ 1 đến " + MAX_IMAGES + " hình ảnh.");
        }
        return given;
    }

    private User requireCustomer(String email) {
        return users.findByEmail(email).filter(u -> u.isActive() && u.getRole().getName() == RoleName.CUSTOMER)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản khách hàng không hợp lệ."));
    }
}
