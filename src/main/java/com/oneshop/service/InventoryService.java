package com.oneshop.service;

/**
 * Deduct, restore and adjust StoreProduct stock, always with an InventoryMovement (BR-09, BR-12).
 *
 * <p>Skeleton for Phases 8, 10 and 12: ORDER (negative), CANCEL_ORDER (positive) and STOCK_ADJUST (with staff and note).
 * Quantity must never go below zero.
 */
public interface InventoryService {
}
