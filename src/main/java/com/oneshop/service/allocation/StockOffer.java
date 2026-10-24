package com.oneshop.service.allocation;

/**
 * What one Store can sell of one SKU right now: {@code available = quantity - reserved_quantity} of an ACTIVE
 * StoreProduct at an eligible Store. Built by the caller; the engine never reads the database.
 */
public record StockOffer(Long storeProductId, Long storeId, Long productId, int available) {
}
