package com.oneshop.service;

/**
 * Deduct, restore and adjust StoreProduct stock, always with an InventoryMovement (BR-09, BR-12).
 *
 * <p>Phase 6 only provides {@link #adjustStock}, which the Admin StoreProduct form needs. ORDER (negative) and
 * CANCEL_ORDER (positive) movements arrive with Phases 8 and 12; the Staff stock screen of Phase 10 reuses
 * {@code adjustStock} after checking the Staff's Store scope. Quantity must never go below zero.
 */
public interface InventoryService {

    /**
     * Sets the real quantity of a StoreProduct and records the change as a STOCK_ADJUST InventoryMovement
     * (before / after / who / note). The row is locked while it changes. Nothing is written when the quantity is
     * already {@code newQuantity}.
     *
     * <p>The caller decides whether the user may touch that Store; this method does not check a Store scope.
     *
     * @param userEmail e-mail of the authenticated Admin or Staff making the change
     * @param note      reason of the change; a default text is stored when it is blank
     * @throws com.oneshop.exception.BadRequestException if {@code newQuantity} is negative
     */
    void adjustStock(Long storeProductId, int newQuantity, String userEmail, String note);
}
