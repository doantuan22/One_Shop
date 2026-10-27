package com.oneshop.dto.response;

import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @param storeName       the Store, or "Nhiều chi nhánh (n)" for an Order supplied by several Stores
 * @param paymentDeadline end of the ONLINE payment window ({@code created_at + 15 minutes}), or {@code null}
 * @param expired         the window has ended; a still pending Order is then waiting for the timeout handling (BE-05)
 */
public record OrderPaymentResponse(Long orderId, Long checkoutId, String storeName, BigDecimal totalAmount,
                                   PaymentMethod paymentMethod, OrderPaymentStatus paymentStatus,
                                   OrderStatus orderStatus, List<PaymentResponse> attempts,
                                   LocalDateTime paymentDeadline, boolean expired, boolean gatewayEnabled,
                                   String ledgerState, boolean reconciling, boolean realGateway) {
    /** Shape before payOS (no real-gateway flag). */
    public OrderPaymentResponse(Long orderId, Long checkoutId, String storeName, BigDecimal totalAmount,
                                PaymentMethod paymentMethod, OrderPaymentStatus paymentStatus,
                                OrderStatus orderStatus, List<PaymentResponse> attempts,
                                LocalDateTime paymentDeadline, boolean expired, boolean gatewayEnabled,
                                String ledgerState, boolean reconciling) {
        this(orderId, checkoutId, storeName, totalAmount, paymentMethod, paymentStatus, orderStatus, attempts,
                paymentDeadline, expired, gatewayEnabled, ledgerState, reconciling, false);
    }

    /** Shape of BE-03 (deadline, no gateway information). */
    public OrderPaymentResponse(Long orderId, Long checkoutId, String storeName, BigDecimal totalAmount,
                                PaymentMethod paymentMethod, OrderPaymentStatus paymentStatus,
                                OrderStatus orderStatus, List<PaymentResponse> attempts,
                                LocalDateTime paymentDeadline, boolean expired) {
        this(orderId, checkoutId, storeName, totalAmount, paymentMethod, paymentStatus, orderStatus, attempts,
                paymentDeadline, expired, false, null, false, false);
    }


    /** Legacy shape without a deadline, kept for V2 callers and tests. */
    public OrderPaymentResponse(Long orderId, Long checkoutId, String storeName, BigDecimal totalAmount,
                                PaymentMethod paymentMethod, OrderPaymentStatus paymentStatus,
                                OrderStatus orderStatus, List<PaymentResponse> attempts) {
        this(orderId, checkoutId, storeName, totalAmount, paymentMethod, paymentStatus, orderStatus, attempts, null,
                false, false, null, false, false);
    }

    /** The demo gateway offers its scenarios only while the customer can still pay a not-yet-submitted attempt. */
    public boolean canSimulate() {
        return gatewayEnabled && payable() && "AWAITING".equals(ledgerState);
    }

    public boolean payable() {
        return paymentMethod == PaymentMethod.ONLINE && orderStatus == OrderStatus.PENDING_PAYMENT
                && paymentStatus == OrderPaymentStatus.UNPAID && !expired;
    }

    /** Whole seconds left to pay ({@code 0} once the window has ended or when there is no deadline). */
    public long remainingSeconds() {
        return paymentDeadline == null ? 0
                : Math.max(0, java.time.Duration.between(java.time.LocalDateTime.now(), paymentDeadline).getSeconds());
    }

    public boolean hasPendingAttempt() {
        return attempts.stream().anyMatch(payment -> payment.status() == PaymentStatus.PENDING);
    }
}
