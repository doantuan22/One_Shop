package com.oneshop.service;

import com.oneshop.entity.Order;
import com.oneshop.entity.StoreProduct;

/**
 * Deduct, restore and adjust StoreProduct stock, always with an InventoryMovement (BR-09, BR-12).
 *
 * <p>{@link #adjustStock} (STOCK_ADJUST) serves the Admin StoreProduct form and, from Phase 10, the Staff stock
 * screen after its Store scope check. {@link #deductForOrder} (ORDER) is used by checkout.
 * {@link #restoreForCancelledOrder} (positive CANCEL_ORDER) supports ONLINE failure in Phase 9.1.
 * Quantity must never go below zero.
 */
public interface InventoryService {

    /**
     * Sets the real quantity of a StoreProduct and records the change as a STOCK_ADJUST InventoryMovement
     * (before / after / who / note). The row is locked while it changes. Nothing is written when the quantity is
     * already {@code newQuantity}.
     *
     * <p>The caller decides whether the user may touch that Store; this method does not check a Store scope.
     * Staff callers use {@link StaffStoreScopeService#requireStoreProduct} for scoped reads and
     * {@link StaffStoreScopeService#requireAssignedStore} on the locked resource's actual Store before a mutation,
     * in the same transaction. Request store_id is never authorization; Phase 10.4 wraps this writer in
     * {@link StaffInventoryService} without changing checkout/restoration behavior.
     *
     * @param userEmail e-mail of the authenticated Admin or Staff making the change
     * @param note      reason of the change; a default text is stored when it is blank
     * @throws com.oneshop.exception.BadRequestException if {@code newQuantity} is negative
     */
    void adjustStock(Long storeProductId, int newQuantity, String userEmail, String note);

    /**
     * Takes the quantity of an Order line out of the stock of its StoreProduct and records it as an ORDER
     * InventoryMovement (negative change, before / after, the Order). Runs inside the caller's transaction and never
     * commits on its own.
     *
     * @param lockedStoreProduct the StoreProduct, already loaded with a write lock in the current transaction, so the
     *                           quantity read here cannot change before the transaction ends
     * @param order              the saved Order the stock is taken for
     * @throws com.oneshop.exception.BadRequestException if the quantity is not positive or exceeds the stock; the
     *                                                   stock is then left untouched
     */
    void deductForOrder(StoreProduct lockedStoreProduct, int quantity, Order order);

    /** Restore every snapshot line under ascending StoreProduct write locks, with CANCEL_ORDER movements.
     * Caller holds the Order lock and validates its transition; shares that transaction without committing. */
    void restoreForCancelledOrder(Order lockedOrder);
}
