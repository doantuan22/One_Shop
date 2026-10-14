package com.oneshop.service;

import com.oneshop.dto.request.AddCartItemRequest;
import com.oneshop.dto.response.CartResponse;

import java.util.Collection;

/**
 * V3 central cart: one line per Product/SKU, independent of the selected browsing Store. A nullable legacy
 * StoreProduct reference may remain for compatibility; it never chooses the checkout source or the online price.
 *
 * <p>The customer is always the authenticated user, identified by the e-mail of the security principal; no method
 * takes a user id or a cart id from the request. A line id that is not in that customer's own cart is treated as not
 * existing.
 *
 * <p>The cart only <em>checks</em> stock, against the database, each time a line is added or changed. It never
 * reserves or deducts stock and writes no InventoryMovement: that happens at checkout, which loads and checks
 * everything again. Available stock is summed across eligible Stores after reservations; prices come from Product.
 * Changing the selected Store never touches the cart (BR-16).
 */
public interface CartService {

    /**
     * The flat Product cart, with the current price and purchasability of each line. A line that can no longer
     * be bought stays in the cart and is marked; nothing is removed or changed here.
     */
    CartResponse getCart(String customerEmail);

    /**
     * Puts a Product into the cart. If the cart already has a line for that Product its quantity grows;
     * a second line is never created (TC-04). The cart itself is created on the first add.
     *
     * @throws com.oneshop.exception.BadRequestException if the StoreProduct is not on sale or the Store does not have
     *                                                   enough chain availability for the resulting quantity
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
     * The Product lines the customer chose to check out: the hand-over to allocation. Every id must be a
     * line of this customer's cart that can be bought right now. Nothing is created or changed.
     *
     * @throws com.oneshop.exception.BadRequestException if nothing is chosen, an id is not one of the customer's
     *                                                   lines, or a chosen line cannot be bought right now
     */
    CartResponse getSelection(String customerEmail, Collection<Long> cartItemIds);
}
