package com.oneshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Durable journal of a provider webhook; the unique (provider, event_key) is what makes a repeated delivery harmless. */
@Entity
@Table(name = "payment_provider_events")
public class PaymentProviderEvent {

    public enum Outcome { APPLIED, TRIGGERED_SYNC, UNKNOWN_ORDER, REJECTED_MISMATCH }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentProvider provider;

    @Column(name = "event_key", nullable = false, length = 128)
    private String eventKey;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "order_code")
    private Long orderCode;

    @Column(name = "payment_link_id", length = 64)
    private String paymentLinkId;

    @Column(length = 100)
    private String reference;

    private Long amount;

    @Column(name = "event_code", length = 10)
    private String eventCode;

    @Column(name = "transaction_at")
    private LocalDateTime transactionAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Outcome outcome;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt = LocalDateTime.now().withNano(0);

    public Long getId() { return id; }
    public PaymentProvider getProvider() { return provider; }
    public void setProvider(PaymentProvider provider) { this.provider = provider; }
    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }
    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public Long getOrderCode() { return orderCode; }
    public void setOrderCode(Long orderCode) { this.orderCode = orderCode; }
    public String getPaymentLinkId() { return paymentLinkId; }
    public void setPaymentLinkId(String paymentLinkId) { this.paymentLinkId = paymentLinkId; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
    public String getEventCode() { return eventCode; }
    public void setEventCode(String eventCode) { this.eventCode = eventCode; }
    public LocalDateTime getTransactionAt() { return transactionAt; }
    public void setTransactionAt(LocalDateTime transactionAt) { this.transactionAt = transactionAt; }
    public Outcome getOutcome() { return outcome; }
    public void setOutcome(Outcome outcome) { this.outcome = outcome; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
}
