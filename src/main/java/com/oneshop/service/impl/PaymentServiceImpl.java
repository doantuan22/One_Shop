package com.oneshop.service.impl;

import com.oneshop.config.PaymentProperties;
import com.oneshop.dto.response.OrderPaymentResponse;
import com.oneshop.dto.response.PaymentResponse;
import com.oneshop.entity.Order;
import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.OrderFulfillment;
import com.oneshop.entity.OrderFulfillmentStatus;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.PaymentStatus;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.OrderFulfillmentRepository;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.PaymentRepository;
import com.oneshop.service.PaymentDeadlinePolicy;
import com.oneshop.service.PaymentService;
import com.oneshop.service.payment.LedgerState;
import com.oneshop.service.payment.MockLedger;
import com.oneshop.service.payment.PaymentGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** ONLINE workflow and COD collection. Every mutation starts with the caller's or this service's Order lock. */
@Service
@Transactional
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    public PaymentServiceImpl(PaymentRepository paymentRepository, OrderRepository orderRepository) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
    }

    /** The payment window (created_at + 15 minutes). */
    private PaymentDeadlinePolicy deadlines = new PaymentDeadlinePolicy(new PaymentProperties(), Clock.systemDefaultZone());

    @Autowired
    void setDeadlines(PaymentDeadlinePolicy deadlines) {
        this.deadlines = deadlines;
    }

    private PaymentGateway gateway;

    /** The mock gateway, for the ledger state shown on the payment page. */
    @Autowired(required = false)
    void setGateway(PaymentGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderPaymentResponse getOrderPayments(String customerEmail, Long orderId) {
        Order order = orderRepository.findByIdAndUserEmail(orderId, customerEmail)
                .orElseThrow(PaymentServiceImpl::orderNotFound);
        List<Payment> rows = paymentRepository.findByOrderIdOrderByIdAsc(orderId);
        String ledger = null;
        boolean reconciling = false;
        if (order.getPaymentMethod() == PaymentMethod.ONLINE) {
            // the state of the attempt the customer can act on: the latest one that is still pending
            LedgerState state = rows.stream().filter(p -> p.getStatus() == PaymentStatus.PENDING)
                    .map(MockLedger::stateOf).findFirst().orElse(null);
            ledger = state == null ? null : state.name();
            reconciling = state == LedgerState.IN_DOUBT && order.getOrderStatus() == OrderStatus.PENDING_PAYMENT
                    && deadlines.isExpired(order);
        }
        return new OrderPaymentResponse(order.getId(), order.getCheckoutSession().getId(), storeLabel(order),
                order.getTotalAmount(), order.getPaymentMethod(), order.getPaymentStatus(), order.getOrderStatus(),
                rows.stream().map(PaymentServiceImpl::response).toList(),
                deadlines.paymentDeadline(order), deadlines.isExpired(order),
                gateway != null && gateway.enabled(), ledger, reconciling);
    }

    @Override
    public PaymentResponse createOnlinePaymentAttempt(String customerEmail, Long orderId) {
        Order order = ownLockedOrder(customerEmail, orderId);
        requirePayable(order);
        Payment payment = paymentRepository.findFirstByOrderIdAndStatusOrderByIdAsc(orderId, PaymentStatus.PENDING)
                .orElseGet(() -> {
                    Payment attempt = new Payment();
                    attempt.setOrder(order);
                    attempt.setMethod(order.getPaymentMethod());
                    attempt.setAmount(order.getTotalAmount());
                    attempt.setStatus(PaymentStatus.PENDING);
                    attempt.setTransactionCode(mockReference());
                    return paymentRepository.saveAndFlush(attempt);
                });
        requireConsistent(payment, order);
        return response(payment);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentResponse recordCodCollected(Order order) {
        if (order == null || order.getId() == null || order.getFulfillmentType() != FulfillmentType.DELIVERY
                || order.getPaymentMethod() != PaymentMethod.COD || order.getOrderStatus() != OrderStatus.SHIPPING
                || order.getPaymentStatus() != OrderPaymentStatus.UNPAID
                || order.getTotalAmount() == null || order.getTotalAmount().signum() < 0) {
            throw new BadRequestException("Chỉ ghi nhận thu COD cho đơn giao hàng đang SHIPPING và chưa thanh toán.");
        }
        return collectOffline(order);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentResponse recordPayAtStoreCollected(Order order) {
        if (order == null || order.getId() == null || order.getFulfillmentType() != FulfillmentType.STORE_PICKUP
                || order.getPaymentMethod() != PaymentMethod.PAY_AT_STORE || order.getOrderStatus() != OrderStatus.READY_FOR_PICKUP
                || order.getPaymentStatus() != OrderPaymentStatus.UNPAID || order.getPickupCode() == null
                || order.getReadyAt() == null || order.getTotalAmount() == null || order.getTotalAmount().signum() < 0) {
            throw new BadRequestException("Chỉ ghi nhận thu tại cửa hàng cho đơn pickup sẵn sàng nhận và chưa thanh toán.");
        }
        return collectOffline(order);
    }

    private OrderFulfillmentRepository fulfillmentRepository;

    /** Fulfillment count constrains offline collection to a single-source Order. */
    @Autowired
    void setFulfillmentRepository(OrderFulfillmentRepository fulfillmentRepository) {
        this.fulfillmentRepository = fulfillmentRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentResponse recordOfflineCollected(Order order, OrderFulfillment fulfillment) {
        if (order == null || fulfillment == null || order.getId() == null || fulfillment.getOrder() == null
                || !order.getId().equals(fulfillment.getOrder().getId())
                || order.getPaymentStatus() != OrderPaymentStatus.UNPAID
                || order.getTotalAmount() == null || order.getTotalAmount().signum() < 0) {
            throw new BadRequestException("Chỉ ghi nhận thu tiền cho đơn chưa thanh toán và đúng phần giao của đơn.");
        }
        OrderStatus status = order.getOrderStatus();
        if (status == OrderStatus.PENDING_PAYMENT || status == OrderStatus.CANCELLED
                || status == OrderStatus.NEEDS_ATTENTION || status == OrderStatus.COMPLETED) {
            throw new BadRequestException("Đơn hàng ở trạng thái hiện tại không được ghi nhận thu tiền.");
        }
        boolean cod = order.getPaymentMethod() == PaymentMethod.COD
                && fulfillment.getFulfillmentType() == FulfillmentType.DELIVERY
                && fulfillment.getStatus() == OrderFulfillmentStatus.SHIPPING;
        boolean payAtStore = order.getPaymentMethod() == PaymentMethod.PAY_AT_STORE
                && fulfillment.getFulfillmentType() == FulfillmentType.STORE_PICKUP
                && fulfillment.getStatus() == OrderFulfillmentStatus.READY_FOR_PICKUP
                && fulfillment.getPickupCode() != null && fulfillment.getReadyAt() != null;
        if (!cod && !payAtStore) {
            throw new BadRequestException("Phần giao chưa ở bước được phép thu tiền.");
        }
        if (fulfillmentRepository != null && fulfillmentRepository.countByOrderId(order.getId()) != 1) {
            throw new BadRequestException("Đơn có nhiều phần giao không thu tiền theo từng phần.");
        }
        return collectOffline(order);
    }

    private PaymentResponse collectOffline(Order order) {
        if (paymentRepository.findFirstByOrderIdAndStatusOrderByIdAsc(order.getId(), PaymentStatus.SUCCESS).isPresent()) {
            throw new BadRequestException("Đơn hàng đã có thanh toán thành công; không thể thu tiền lần nữa.");
        }
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setMethod(order.getPaymentMethod());
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setTransactionCode(transactionCode());
        payment.setPaidAt(LocalDateTime.now().withNano(0));
        order.setPaymentStatus(OrderPaymentStatus.PAID);
        return response(paymentRepository.saveAndFlush(payment));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentResponse initializeOnlineAttempt(Order order) {
        if (order == null || order.getId() == null || order.getPaymentMethod() != PaymentMethod.ONLINE
                || order.getOrderStatus() != OrderStatus.PENDING_PAYMENT
                || order.getPaymentStatus() != OrderPaymentStatus.UNPAID
                || order.getTotalAmount() == null || order.getTotalAmount().signum() < 0) {
            throw new BadRequestException("Chỉ khởi tạo thanh toán cho đơn ONLINE đang chờ thanh toán.");
        }
        if (!paymentRepository.findByOrderIdOrderByIdAsc(order.getId()).isEmpty()) {
            throw new BadRequestException("Đơn hàng đã có lần thanh toán; không khởi tạo thêm.");
        }
        Payment attempt = new Payment();
        attempt.setOrder(order);
        attempt.setMethod(PaymentMethod.ONLINE);
        attempt.setAmount(order.getTotalAmount()); // the whole Order, never a Fulfillment or a Store
        attempt.setStatus(PaymentStatus.PENDING);
        attempt.setTransactionCode(mockReference());
        return response(paymentRepository.saveAndFlush(attempt));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void closePendingForCustomerCancellation(Order order) {
        if (order == null || order.getPaymentStatus() != OrderPaymentStatus.UNPAID) {
            throw new BadRequestException("Chỉ hủy đơn chưa thanh toán.");
        }
        var attempts = paymentRepository.findByOrderIdOrderByIdAsc(order.getId());
        // Refuse inconsistent/collected data before any side effect, rather than repairing payment records.
        for (var attempt : attempts) {
            if (attempt.getStatus() == PaymentStatus.SUCCESS || attempt.getPaidAt() != null
                    || attempt.getMethod() != order.getPaymentMethod() || attempt.getAmount() == null
                    || attempt.getAmount().compareTo(order.getTotalAmount()) != 0) {
                throw new BadRequestException("Thanh toán của đơn hàng không nhất quán để hủy.");
            }
        }
        for (var attempt : attempts) {
            if (attempt.getStatus() == PaymentStatus.PENDING) {
                attempt.setStatus(PaymentStatus.FAILED);
                attempt.setTransactionCode(transactionCode());
                // Customer cancellation is not a collection. Order remains UNPAID; no receipt or paid_at is added.
            }
        }
        paymentRepository.flush();
    }

    private Order ownLockedOrder(String customerEmail, Long orderId) {
        // Filter on the PK alone to avoid locking unrelated Orders through a user join on SQL Server.
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(PaymentServiceImpl::orderNotFound);
        if (!order.getUser().getEmail().equals(customerEmail)) {
            throw orderNotFound();
        }
        return order;
    }

    private void requirePayable(Order order) {
        if (order.getPaymentMethod() != PaymentMethod.ONLINE) {
            throw new BadRequestException("Đơn hàng này không sử dụng thanh toán trực tuyến.");
        }
        if (order.getOrderStatus() != OrderStatus.PENDING_PAYMENT || order.getPaymentStatus() != OrderPaymentStatus.UNPAID) {
            throw new BadRequestException("Đơn hàng không còn chờ thanh toán trực tuyến.");
        }
        if (!deadlines.isPayableNow(order)) {
            throw new BadRequestException("Đơn hàng đã quá hạn thanh toán trực tuyến.");
        }
    }

    private static void requireConsistent(Payment payment, Order order) {
        if (order.getPaymentMethod() != PaymentMethod.ONLINE || payment.getMethod() != order.getPaymentMethod()
                || payment.getAmount() == null || payment.getAmount().compareTo(order.getTotalAmount()) != 0) {
            throw new BadRequestException("Phương thức hoặc số tiền thanh toán không khớp với đơn hàng.");
        }
    }

    private static ResourceNotFoundException orderNotFound() {
        return new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn.");
    }

    private static String transactionCode() { return "PAY-" + UUID.randomUUID(); }

    /** Reference made by the backend for the mock gateway; the browser never supplies it. */
    private static String mockReference() { return MockLedger.newReference(); }

    private static String storeLabel(Order order) {
        if (order.getStore() != null) {
            return order.getStore().getName();
        }
        long stores = order.getFulfillments().stream().map(f -> f.getStore().getId()).distinct().count();
        return stores > 1 ? "Nhiều chi nhánh (" + stores + ")" : "OneShop";
    }

    private static PaymentResponse response(Payment payment) {
        // the in-doubt marker is an internal ledger detail, never shown as part of the reference
        return new PaymentResponse(payment.getId(), payment.getOrder().getId(), payment.getMethod(), payment.getAmount(),
                payment.getStatus(), MockLedger.reference(payment.getTransactionCode()), payment.getPaidAt(),
                payment.getCreatedAt());
    }
}
