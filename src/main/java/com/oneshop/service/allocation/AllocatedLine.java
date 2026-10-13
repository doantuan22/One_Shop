package com.oneshop.service.allocation;

/** One SKU taken from one StoreProduct: becomes one OrderItem in the Fulfillment of that Store. */
public record AllocatedLine(Long productId, int quantity, Long storeProductId) {
}
