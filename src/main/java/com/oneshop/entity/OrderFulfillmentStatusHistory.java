package com.oneshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Audit history for status transitions of individual branch fulfillments.
 */
@Entity
@Table(name = "order_fulfillment_status_history")
@EntityListeners(AuditingEntityListener.class)
public class OrderFulfillmentStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fulfillment_id", nullable = false)
    private OrderFulfillment fulfillment;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 30)
    private OrderFulfillmentStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 30)
    private OrderFulfillmentStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_user_id")
    private User changedBy;

    @Column(length = 500)
    private String note;

    @CreatedDate
    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    public Long getId() {
        return id;
    }

    public OrderFulfillment getFulfillment() {
        return fulfillment;
    }

    public void setFulfillment(OrderFulfillment fulfillment) {
        this.fulfillment = fulfillment;
    }

    public OrderFulfillmentStatus getOldStatus() {
        return oldStatus;
    }

    public void setOldStatus(OrderFulfillmentStatus oldStatus) {
        this.oldStatus = oldStatus;
    }

    public OrderFulfillmentStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(OrderFulfillmentStatus newStatus) {
        this.newStatus = newStatus;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(User changedBy) {
        this.changedBy = changedBy;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }
}

