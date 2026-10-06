package com.oneshop.service;

import com.oneshop.dto.request.CheckoutRequest;
import com.oneshop.dto.response.CheckoutPreviewResponse;
import com.oneshop.dto.response.CheckoutResultResponse;

import java.util.Collection;

/**
 * Turns the cart lines a customer chose into one CheckoutSession and one Order per Store (BR-07 to BR-10).
 *
 * <p>The customer is always the authenticated user (e-mail of the security principal). Nothing the cart or the
 * browser showed is trusted: lines, StoreProducts, prices and stock are read again from the database, and the Store of
 * an Order is the Store of its StoreProducts, never a value from the request or the selected Store.
 *
 * <p>This phase stops at the created Orders. Collecting payments, moving an Order through its fulfillment states and
 * cancelling belong to later phases.
 */
public interface CheckoutService {

    /**
     * The chosen lines grouped by Store with current prices, for the checkout page. Read only.
     *
     * @throws com.oneshop.exception.BadRequestException if nothing is chosen, an id is not a line of this customer's
     *                                                   cart, or a chosen line cannot be bought right now
     */
    CheckoutPreviewResponse prepare(String customerEmail, Collection<Long> cartItemIds);

    /**
     * Places the order, all or nothing, in one transaction:
     * <ol>
     *   <li>loads the chosen lines from the customer's own cart;</li>
     *   <li>locks their StoreProducts (ascending id) and reads price, stock and status under that lock;</li>
     *   <li>checks every line and every Store group; one failure fails the whole checkout;</li>
     *   <li>creates the CheckoutSession, one Order per Store with its own fulfillment type and payment method, and
     *       OrderItems that freeze product name, unit price, quantity and subtotal;</li>
     *   <li>deducts stock with an ORDER InventoryMovement per line;</li>
     *   <li>removes exactly the checked-out lines from the cart.</li>
     * </ol>
     * If anything fails, nothing of the above is kept and the cart is as it was.
     *
     * @throws com.oneshop.exception.BadRequestException if the request or the current data does not allow the order
     */
    CheckoutResultResponse placeOrder(String customerEmail, CheckoutRequest request);

    /**
     * A checkout of this customer with the Orders it created and their item snapshots.
     *
     * @throws com.oneshop.exception.ResourceNotFoundException if the customer has no such checkout
     */
    CheckoutResultResponse getCheckout(String customerEmail, Long checkoutId);
}
