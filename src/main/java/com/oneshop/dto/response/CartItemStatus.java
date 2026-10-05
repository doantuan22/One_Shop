package com.oneshop.dto.response;

/**
 * Whether a cart line can be bought right now, judged against the current database state each time the cart is
 * shown. The exact stock is never part of it (BR-15).
 */
public enum CartItemStatus {

    /** On sale at its Store and the Store has enough stock for the quantity in the cart. */
    AVAILABLE,

    /** On sale, but the Store has less stock than the quantity in the cart: the customer must lower it. */
    INSUFFICIENT_STOCK,

    /** On sale, but the Store has none left. */
    OUT_OF_STOCK,

    /** No longer on sale at that Store (StoreProduct, Store, Product, Category or Brand not ACTIVE). */
    UNAVAILABLE
}
