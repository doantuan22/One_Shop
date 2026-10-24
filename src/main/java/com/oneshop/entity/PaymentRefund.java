package com.oneshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One SIMULATED refund of money collected for an Order (OPEN-10.1): what was given back, for which Fulfillment, why, by
 * whom and when. It is history, never a bank movement. The database keeps one GOODS refund per Fulfillment, one SHIPPING
 * refund per Order and one WHOLE_ORDER refund per Payment, so a repeated request cannot refund anything twice.
 * {@code requestedBy} is {@code null} when the system acted (timeout, customer cancel recorded without an Admin).
 */
@Entity
@Table(name = "payment_refunds")
public class PaymentRefund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refund_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fulfillment_id")
    private OrderFulfillment fulfillment;

    /** Set only for the RETURN component. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_id")
    private OrderReturn orderReturn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentRefundComponent component;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentRefundStatus status = PaymentRefundStatus.PENDING_SIMULATION;

    @Column(nullable = false, length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id")
    private User requestedBy;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    public Long getId() {
        return id;
    }

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public OrderFulfillment getFulfillment() {
        return fulfillment;
    }

    public void setFulfillment(OrderFulfillment fulfillment) {
        this.fulfillment = fulfillment;
    }

    public OrderReturn getOrderReturn() {
        return orderReturn;
    }

    public void setOrderReturn(OrderReturn orderReturn) {
        this.orderReturn = orderReturn;
    }

    public PaymentRefundComponent getComponent() {
        return component;
    }

    public void setComponent(PaymentRefundComponent component) {
        this.component = component;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public PaymentRefundStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentRefundStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public User getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(User requestedBy) {
        this.requestedBy = requestedBy;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getRefundedAt() {
        return refundedAt;
    }

    public void setRefundedAt(LocalDateTime refundedAt) {
        this.refundedAt = refundedAt;
    }
}
