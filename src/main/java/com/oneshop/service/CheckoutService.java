package com.oneshop.service;

import com.oneshop.dto.request.CheckoutRequest;
import com.oneshop.dto.request.PlaceOrderRequest;
import com.oneshop.dto.response.CheckoutPreviewResponse;
import com.oneshop.dto.response.CheckoutResultResponse;
import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.exception.BadRequestException;

import java.util.Collection;

/**
 * V3 Centralized Checkout. Turns the cart lines a customer chose into one CheckoutSession and:
 * <ul>
 *   <li><b>ONLINE</b> (any number of Stores): exactly one Order, one payment obligation, one Fulfillment per supplying
 *       Store; the Order waits in PENDING_PAYMENT for 15 minutes;</li>
 *   <li><b>COD from one Store</b>: one Order; <b>COD that needs several Stores</b>: only after the customer's explicit
 *       consent, one independent Order per Store (never a parent/child Order);</li>
 *   <li><b>STORE_PICKUP</b>: one Order and one Fulfillment at the Store the customer chose, which must hold the
 *       whole cart.</li>
 * </ul>
 * Stock is deducted at once, inside the same transaction, with an ORDER InventoryMovement per StoreProduct.
 *
 * <p>The customer never chooses a Store for DELIVERY and nothing the browser showed is trusted: lines, prices,
 * stock, the allocation and the total are read and decided again from the database under row locks.
 *
 * <p>Payment collection, the timeout, reconciliation, cancellation and refund simulation are BE-05; Staff handling
 * of Fulfillments is BE-04.
 */
public interface CheckoutService {

    /** The checkout page for the chosen lines with its default choices (delivery, COD). Read only. */
    CheckoutPreviewResponse prepare(String customerEmail, Collection<Long> cartItemIds);

    /**
     * The checkout page for the chosen lines and choices: allocation plan, shipping fee, number of Orders and total.
     * Read only; it reserves nothing and the plan may be outdated by the time the order is placed.
     *
     * @throws BadRequestException if nothing is chosen, an id is not a line of this customer's cart, or a chosen
     *                             line cannot be bought right now
     */
    default CheckoutPreviewResponse preview(String customerEmail, Collection<Long> cartItemIds,
                                            FulfillmentType fulfillmentType, PaymentMethod paymentMethod,
                                            Long pickupStoreId, String shippingAddress) {
        return preview(customerEmail, cartItemIds, fulfillmentType, paymentMethod, pickupStoreId, shippingAddress, null);
    }

    /**
     * The same preview with the promotion code the customer picked (blank for none). The page also lists every open
     * code with whether it works for this cart. A code that cannot be used is reported in
     * {@code promotionProblem}; it never fails the preview and nothing is spent.
     */
    CheckoutPreviewResponse preview(String customerEmail, Collection<Long> cartItemIds,
                                    FulfillmentType fulfillmentType, PaymentMethod paymentMethod, Long pickupStoreId,
                                    String shippingAddress, String promotionCode);

    /**
     * Places the order, all or nothing, in one transaction: loads the lines of the customer's own cart under the
     * cart lock; allocates under row locks; creates the session, Order(s), Fulfillments and OrderItems with the
     * single online price; deducts stock with ORDER movements; writes the initial histories; creates the PENDING
     * payment attempt of an ONLINE Order; removes exactly the checked-out lines. If anything fails nothing is kept.
     *
     * @throws com.oneshop.exception.CheckoutConflictException if what the customer confirmed no longer matches
     *                                                         (plan, total), the COD split consent is missing, or the
     *                                                         flow is closed pending a business decision
     * @throws BadRequestException                             if the request or the current data does not allow it
     */
    CheckoutResultResponse placeOrder(String customerEmail, PlaceOrderRequest request);

    /**
     * V2 checkout (one Order per Store, a Store chosen per group). It no longer exists: V3 allocates the Store and
     * never splits an ONLINE order into several Orders. Always refused.
     *
     * @deprecated use {@link #placeOrder(String, PlaceOrderRequest)}
     */
    @Deprecated
    default CheckoutResultResponse placeOrder(String customerEmail, CheckoutRequest legacyRequest) {
        throw new BadRequestException("Luồng đặt hàng theo từng chi nhánh (V2) đã ngừng. "
                + "Vui lòng đặt hàng lại từ giỏ hàng.");
    }

    /**
     * A checkout of this customer with the Orders it created, their Fulfillments and item snapshots.
     *
     * @throws com.oneshop.exception.ResourceNotFoundException if the customer has no such checkout
     */
    CheckoutResultResponse getCheckout(String customerEmail, Long checkoutId);
}
