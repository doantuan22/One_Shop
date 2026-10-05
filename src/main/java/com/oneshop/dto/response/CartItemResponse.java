package com.oneshop.dto.response;

import java.math.BigDecimal;

/**
 * One cart line. It always stands for a StoreProduct, that is a SKU at one Store (BR-05).
 *
 * @param unitPrice current price of the StoreProduct, read from the database when the cart is shown. It is not a
 *                  snapshot: the price is only frozen in an OrderItem at checkout.
 * @param subtotal  {@code unitPrice x quantity}, calculated by the server
 */
public record CartItemResponse(Long cartItemId, Long storeProductId, Long productId, String sku, String productName,
                               String imageUrl, BigDecimal unitPrice, int quantity, BigDecimal subtotal,
                               CartItemStatus status) {

    /** Only a line that can be bought right now may be chosen for checkout. */
    public boolean selectable() {
        return status == CartItemStatus.AVAILABLE;
    }
}
