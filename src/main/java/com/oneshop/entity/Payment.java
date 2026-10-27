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

/** Payment record attached to an Order (ORDER 1--N PAYMENT), never to a CheckoutSession (BR-18). */
@Entity
@Table(name = "payments")
public class Payment extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "transaction_code", length = 100)
    private String transactionCode;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", nullable = false, length = 30)
    private PaymentRefundStatus refundStatus = PaymentRefundStatus.NONE;

    @Column(name = "refund_amount", precision = 18, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refund_requested_at")
    private LocalDateTime refundRequestedAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    /** {@code null} for COD / PAY_AT_STORE; MOCK for the simulated gateway; PAYOS for the real hosted checkout. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaymentProvider provider;

    @Column(name = "provider_order_code")
    private Long providerOrderCode;

    @Column(name = "provider_link_id", length = 64)
    private String providerLinkId;

    @Column(name = "checkout_url", length = 500)
    private String checkoutUrl;

    /** Last status payOS reported for the link: evidence, never taken from a browser. */
    @Column(name = "provider_status", length = 20)
    private String providerStatus;

    @Column(name = "provider_amount_paid")
    private Long providerAmountPaid;

    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    /** When the bank recorded the money (converted to the server zone); not when a webhook arrived. */
    @Column(name = "provider_paid_at")
    private LocalDateTime providerPaidAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    public Long getId() {
        return id;
    }

    public PaymentProvider getProvider() { return provider; }
    public void setProvider(PaymentProvider provider) { this.provider = provider; }
    public Long getProviderOrderCode() { return providerOrderCode; }
    public void setProviderOrderCode(Long providerOrderCode) { this.providerOrderCode = providerOrderCode; }
    public String getProviderLinkId() { return providerLinkId; }
    public void setProviderLinkId(String providerLinkId) { this.providerLinkId = providerLinkId; }
    public String getCheckoutUrl() { return checkoutUrl; }
    public void setCheckoutUrl(String checkoutUrl) { this.checkoutUrl = checkoutUrl; }
    public String getProviderStatus() { return providerStatus; }
    public void setProviderStatus(String providerStatus) { this.providerStatus = providerStatus; }
    public Long getProviderAmountPaid() { return providerAmountPaid; }
    public void setProviderAmountPaid(Long providerAmountPaid) { this.providerAmountPaid = providerAmountPaid; }
    public String getProviderReference() { return providerReference; }
    public void setProviderReference(String providerReference) { this.providerReference = providerReference; }
    public LocalDateTime getProviderPaidAt() { return providerPaidAt; }
    public void setProviderPaidAt(LocalDateTime providerPaidAt) { this.providerPaidAt = providerPaidAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(LocalDateTime lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }

    public boolean isPayos() { return provider == PaymentProvider.PAYOS; }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public void setMethod(PaymentMethod method) {
        this.method = method;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getTransactionCode() {
        return transactionCode;
    }

    public void setTransactionCode(String transactionCode) {
        this.transactionCode = transactionCode;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public PaymentRefundStatus getRefundStatus() {
        return refundStatus;
    }

    public void setRefundStatus(PaymentRefundStatus refundStatus) {
        this.refundStatus = refundStatus;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount;
    }

    public LocalDateTime getRefundRequestedAt() {
        return refundRequestedAt;
    }

    public void setRefundRequestedAt(LocalDateTime refundRequestedAt) {
        this.refundRequestedAt = refundRequestedAt;
    }

    public LocalDateTime getRefundedAt() {
        return refundedAt;
    }

    public void setRefundedAt(LocalDateTime refundedAt) {
        this.refundedAt = refundedAt;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }
}
