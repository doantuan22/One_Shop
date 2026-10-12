package com.oneshop.entity;

/** Values of {@code order_fulfillments.status} and related status history. */
public enum OrderFulfillmentStatus {
    CONFIRMED,
    PREPARING,
    PACKED,
    SHIPPING,
    READY_FOR_PICKUP,
    COMPLETED,
    CANCELLED,
    FAILED
}

