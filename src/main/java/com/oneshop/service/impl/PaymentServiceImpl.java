package com.oneshop.service.impl;

import com.oneshop.dto.response.OrderPaymentResponse;
import com.oneshop.dto.response.PaymentResponse;
import com.oneshop.entity.Order;
import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.PaymentStatus;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.PaymentRepository;
import com.oneshop.service.InventoryService;
import com.oneshop.service.OrderService;
import com.oneshop.service.PaymentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDateTime;
import java.util.UUID;

/** ONLINE workflow and COD collection. Every mutation starts with the caller's or this service's Order lock. */
@Service
@Transactional
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final InventoryService inventoryService;

    public PaymentServiceImpl(PaymentRepository paymentRepository, OrderRepository orderRepository,
                              OrderService orderService, InventoryService inventoryService) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.inventoryService = inventoryService;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderPaymentResponse getOrderPayments(String customerEmail, Long orderId) {
        Order order = orderRepository.findByIdAndUserEmail(orderId, customerEmail)
                .orElseThrow(PaymentServiceImpl::orderNotFound);
        return new OrderPaymentResponse(order.getId(), order.getCheckoutSession().getId(), order.getStore().getName(),
                order.getTotalAmount(), order.getPaymentMethod(), order.getPaymentStatus(), order.getOrderStatus(),
                paymentRepository.findByOrderIdOrderByIdAsc(orderId).stream().map(PaymentServiceImpl::response).toList());
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
                    return paymentRepository.saveAndFlush(attempt);
                });
        requireConsistent(payment, order);
        return response(payment);
    }

    @Override
    public PaymentResponse markOnlinePaymentSuccess(String customerEmail, Long orderId, Long paymentId) {
        return complete(customerEmail, orderId, paymentId, PaymentStatus.SUCCESS);
    }

    @Override
    public PaymentResponse markOnlinePaymentFailed(String customerEmail, Long orderId, Long paymentId) {
        return complete(customerEmail, orderId, paymentId, PaymentStatus.FAILED);
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
        if (paymentRepository.findFirstByOrderIdAndStatusOrderByIdAsc(order.getId(), PaymentStatus.SUCCESS).isPresent()) {
            throw new BadRequestException("Đơn hàng đã có thanh toán thành công; không thể thu COD lần nữa.");
        }
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setMethod(PaymentMethod.COD);
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setTransactionCode(transactionCode());
        payment.setPaidAt(LocalDateTime.now().withNano(0));
        order.setPaymentStatus(OrderPaymentStatus.PAID);
        return response(paymentRepository.saveAndFlush(payment));
    }

    private PaymentResponse complete(String customerEmail, Long orderId, Long paymentId, PaymentStatus result) {
        Order order = ownLockedOrder(customerEmail, orderId);
        Payment payment = paymentRepository.findByIdAndOrderId(paymentId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lần thanh toán của đơn hàng này."));
        requireConsistent(payment, order);
        if (payment.getStatus() == result) {
            // Repeated success stays idempotent after fulfillment advances; never rewind the Order.
            // Repeated results return the same record without repeating stock or history writes.
            boolean matchingOrder = result == PaymentStatus.SUCCESS
                    ? order.getPaymentStatus() == OrderPaymentStatus.PAID
                        && order.getOrderStatus() != null
                        && order.getOrderStatus() != OrderStatus.PENDING_PAYMENT && order.getOrderStatus() != OrderStatus.CANCELLED
                    : order.getPaymentStatus() == OrderPaymentStatus.FAILED && order.getOrderStatus() == OrderStatus.CANCELLED;
            if (!matchingOrder) {
                throw new BadRequestException("Kết quả thanh toán không nhất quán với đơn hàng.");
            }
            return response(payment);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BadRequestException("Lần thanh toán đã kết thúc; không thể đổi kết quả.");
        }
        requirePayable(order);
        payment.setStatus(result);
        payment.setTransactionCode(transactionCode());
        payment.setPaidAt(result == PaymentStatus.SUCCESS ? LocalDateTime.now().withNano(0) : null);
        if (result == PaymentStatus.SUCCESS) {
            order.setPaymentStatus(OrderPaymentStatus.PAID);
            orderService.confirmAfterOnlinePayment(order);
        } else {
            inventoryService.restoreForCancelledOrder(order);
            order.setPaymentStatus(OrderPaymentStatus.FAILED);
            orderService.cancelAfterOnlinePaymentFailure(order);
        }
        paymentRepository.flush(); // Check constraints before returning; every change shares this transaction.
        return response(payment);
    }

    private Order ownLockedOrder(String customerEmail, Long orderId) {
        // Filter on the PK alone to avoid locking unrelated Orders through a user join on SQL Server.
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(PaymentServiceImpl::orderNotFound);
        if (!order.getUser().getEmail().equals(customerEmail)) {
            throw orderNotFound();
        }
        return order;
    }

    private static void requirePayable(Order order) {
        if (order.getPaymentMethod() != PaymentMethod.ONLINE) {
            throw new BadRequestException("Đơn hàng này không sử dụng thanh toán trực tuyến.");
        }
        if (order.getOrderStatus() != OrderStatus.PENDING_PAYMENT || order.getPaymentStatus() != OrderPaymentStatus.UNPAID) {
            throw new BadRequestException("Đơn hàng không còn chờ thanh toán trực tuyến.");
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

    private static PaymentResponse response(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getOrder().getId(), payment.getMethod(), payment.getAmount(),
                payment.getStatus(), payment.getTransactionCode(), payment.getPaidAt(), payment.getCreatedAt());
    }
}
