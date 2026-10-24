package com.oneshop.dto.response;

import java.math.BigDecimal;

/**
 * One cart line. In V3, it represents a SKU (Product) in the central cart.
 *
 * @param unitPrice current unified online price of the Product
 * @param originalPrice original price (if any, for discount display)
 * @param subtotal {@code unitPrice x quantity}, calculated by the server
 */
public record CartItemResponse(Long cartItemId, Long storeProductId, Long productId, String sku, String productName,
                               String imageUrl, BigDecimal unitPrice, BigDecimal originalPrice, int quantity, BigDecimal subtotal,
                               CartItemStatus status) {

    public CartItemResponse(Long cartItemId, Long storeProductId, Long productId, String sku, String productName,
                            String imageUrl, BigDecimal unitPrice, int quantity, BigDecimal subtotal,
                            CartItemStatus status) {
        this(cartItemId, storeProductId, productId, sku, productName, imageUrl, unitPrice, null, quantity, subtotal, status);
    }

    /** Only a line that can be bought right now may be chosen for checkout. */
    public boolean selectable() {
        return status == CartItemStatus.AVAILABLE;
    }
}
