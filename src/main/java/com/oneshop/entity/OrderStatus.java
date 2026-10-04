package com.oneshop.entity;

/** Values of {@code orders.order_status}. Transitions are enforced by OrderService in a later phase. */
public enum OrderStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    PREPARING,
    PACKED,
    SHIPPING,
    READY_FOR_PICKUP,
    COMPLETED,
    CANCELLED
}
