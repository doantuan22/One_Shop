package com.oneshop.entity;

public enum InventoryMovementType {
    ORDER,
    CANCEL_ORDER,
    STOCK_ADJUST,
    /** Staff put a returned line back on the shelf (positive change; the Order and the Staff are recorded) */
    RETURN_ORDER
}
