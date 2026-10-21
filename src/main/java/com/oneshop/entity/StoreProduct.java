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
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A Product offered at one Store. This StoreProduct owns only the Store-specific quantity and selling status;
 * online price is shared for the chain on {@link Product}. The nullable {@link #price} column is legacy compatibility
 * only and must never be used to price a new online order. Stock changes go through InventoryService so that an
 * InventoryMovement is written.
 */
@Entity
@Table(name = "store_products",
        uniqueConstraints = @UniqueConstraint(name = "UQ_store_products_store_product",
                columnNames = {"store_id", "product_id"}))
@EntityListeners(AuditingEntityListener.class)
public class StoreProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "store_product_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** V2 legacy schema column. Leave untouched for compatibility; V3 business logic must not read or write it. */
    @Column(precision = 18, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActiveStatus status = ActiveStatus.ACTIVE;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(int reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public int getAvailableQuantity() {
        return Math.max(0, quantity - reservedQuantity);
    }

    public ActiveStatus getStatus() {
        return status;
    }

    public void setStatus(ActiveStatus status) {
        this.status = status;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return status == ActiveStatus.ACTIVE;
    }
}
