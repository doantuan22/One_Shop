package com.oneshop.service;

import com.oneshop.dto.request.AddCartItemRequest;
import com.oneshop.dto.response.CartResponse;

import java.util.Collection;

/**
 * The cart of a customer. Every line references a StoreProduct, never a bare Product, so the same SKU at two Stores
 * is two different lines, and the cart is shown grouped by Store (BR-05, BR-06).
 *
 * <p>The customer is always the authenticated user, identified by the e-mail of the security principal; no method
 * takes a user id or a cart id from the request. A line id that is not in that customer's own cart is treated as not
 * existing.
 *
 * <p>The cart only <em>checks</em> stock, against the database, each time a line is added or changed. It never
 * reserves or deducts stock and writes no InventoryMovement: that happens at checkout, which loads and checks
 * everything again. Prices are never stored in the cart; they are read from the StoreProduct when the cart is shown.
 * Changing the selected Store never touches the cart (BR-16).
 */
public interface CartService {

    /**
     * The cart grouped by Store, with the current price and purchasability of each line. A line that can no longer
     * be bought stays in the cart and is marked; nothing is removed or changed here.
     */
    CartResponse getCart(String customerEmail);

    /**
     * Puts a StoreProduct into the cart. If the cart already has a line for that StoreProduct its quantity grows;
     * a second line is never created (TC-04). The cart itself is created on the first add.
     *
     * @throws com.oneshop.exception.BadRequestException if the StoreProduct is not on sale or the Store does not have
     *                                                   enough stock for the resulting quantity
     */
    void addItem(String customerEmail, AddCartItemRequest request);

    /**
     * Sets the quantity of one line after checking the latest stock.
     *
     * @throws com.oneshop.exception.ResourceNotFoundException if the customer has no such line
     * @throws com.oneshop.exception.BadRequestException       if the quantity is below 1, the StoreProduct is no
     *                                                         longer on sale or the Store does not have enough stock
     */
    void updateItemQuantity(String customerEmail, Long cartItemId, int quantity);

    /**
     * Removes one line. Stock is not affected, because the cart never held any.
     *
     * @throws com.oneshop.exception.ResourceNotFoundException if the customer has no such line
     */
    void removeItem(String customerEmail, Long cartItemId);

    /**
     * The lines the customer chose to check out, grouped by Store: the hand-over to checkout. Every id must be a
     * line of this customer's cart that can be bought right now. Nothing is created or changed.
     *
     * @throws com.oneshop.exception.BadRequestException if nothing is chosen, an id is not one of the customer's
     *                                                   lines, or a chosen line cannot be bought right now
     */
    CartResponse getSelection(String customerEmail, Collection<Long> cartItemIds);
}
