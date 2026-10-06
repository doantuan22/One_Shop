package com.oneshop.dto.response;

/**
 * What the checkout page shows before the customer confirms: the chosen cart lines grouped by Store at their current
 * prices, and suggested receiver details. Everything here is recalculated when the order is placed.
 *
 * @param selection       the chosen lines, grouped by Store
 * @param receiverName    suggestion from the customer's default address or profile; the customer may change it
 * @param receiverPhone   suggestion, see above
 * @param shippingAddress suggestion from the default address, or {@code null}
 */
public record CheckoutPreviewResponse(CartResponse selection, String receiverName, String receiverPhone,
                                      String shippingAddress) {
}
