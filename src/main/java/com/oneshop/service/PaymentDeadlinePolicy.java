package com.oneshop.service;

import com.oneshop.config.PaymentProperties;
import com.oneshop.entity.Order;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.PaymentMethod;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * The ONLINE payment deadline (DEC-15, DEC-17): {@code orders.created_at + pending-timeout}. It is computed, never
 * stored, never extended, and compared with the injected server clock; no value comes from the browser. A payment is
 * accepted only while {@code now < deadline}; the moment a scheduler happens to run does not extend that right.
 *
 * <p>Timeout worker, reconciliation and refund are BE-05; this class only answers "until when" and "payable now".
 */
@Component
public class PaymentDeadlinePolicy {

    private final PaymentProperties properties;
    private final Clock clock;

    public PaymentDeadlinePolicy(PaymentProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** End of the payment window of an ONLINE Order, or {@code null} for other methods / unsaved Orders. */
    public LocalDateTime paymentDeadline(Order order) {
        if (order == null || order.getPaymentMethod() != PaymentMethod.ONLINE || order.getCreatedAt() == null) {
            return null;
        }
        return persistedCreatedAt(order).plusMinutes(properties.getPendingTimeoutMinutes());
    }

    /**
     * {@code created_at} is DATETIME2(0): SQL Server rounds the application timestamp to whole seconds when it is
     * stored. Rounding the in-memory value the same way makes the deadline identical right after the Order is created
     * and when the Order is read back later.
     */
    private static LocalDateTime persistedCreatedAt(Order order) {
        return order.getCreatedAt().plusNanos(500_000_000).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    }

    /** End of the reconciliation window (BE-05): payment deadline + reconciliation window. */
    public LocalDateTime reconciliationDeadline(Order order) {
        LocalDateTime deadline = paymentDeadline(order);
        return deadline == null ? null : deadline.plusMinutes(properties.getReconciliationWindowMinutes());
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** An ONLINE Order created at or before this instant has passed its payment deadline (timeout scan, Admin list). */
    public LocalDateTime expiredBefore() {
        return now().minusMinutes(properties.getPendingTimeoutMinutes());
    }

    /** True when a customer may still start or complete the payment of this Order. */
    public boolean isPayableNow(Order order) {
        LocalDateTime deadline = paymentDeadline(order);
        return deadline != null
                && order.getOrderStatus() == OrderStatus.PENDING_PAYMENT
                && order.getPaymentStatus() == OrderPaymentStatus.UNPAID
                && now().isBefore(deadline);
    }

    /** True when the deadline has been reached (a still pending Order is then waiting for BE-05's worker). */
    public boolean isExpired(Order order) {
        LocalDateTime deadline = paymentDeadline(order);
        return deadline != null && !now().isBefore(deadline);
    }

    /**
     * Whether Staff may start working on an Order's Fulfillments: ONLINE only after a verified payment
     * ({@code PAID} and past {@code PENDING_PAYMENT}); never for {@code PENDING_PAYMENT}, {@code NEEDS_ATTENTION} or
     * {@code CANCELLED}. COD and PAY_AT_STORE start {@code CONFIRMED} and are collected later.
     */
    public boolean allowsFulfillmentWork(Order order) {
        if (order == null || order.getOrderStatus() == null) {
            return false;
        }
        OrderStatus status = order.getOrderStatus();
        if (status == OrderStatus.PENDING_PAYMENT || status == OrderStatus.NEEDS_ATTENTION
                || status == OrderStatus.CANCELLED) {
            return false;
        }
        return order.getPaymentMethod() != PaymentMethod.ONLINE || order.getPaymentStatus() == OrderPaymentStatus.PAID;
    }
}
