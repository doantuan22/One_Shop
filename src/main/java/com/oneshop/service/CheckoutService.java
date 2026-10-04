package com.oneshop.service;

/**
 * Turns the selected cart items into one CheckoutSession and one Order per Store (BR-07 to BR-09).
 *
 * <p>Skeleton for Phase 8. The order-creating method must be {@code @Transactional}, reload prices and stock from the
 * database, lock the StoreProducts with {@code StoreProductRepository#findAllByIdForUpdate}, and roll everything back if
 * any Store group fails.
 */
public interface CheckoutService {
}
