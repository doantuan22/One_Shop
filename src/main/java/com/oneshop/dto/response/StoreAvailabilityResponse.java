package com.oneshop.dto.response;

import java.math.BigDecimal;

/**
 * Client view of a StoreProduct. Deliberately has no quantity: clients only see in stock / out of stock (BR-15).
 * {@code price} is always the shared {@code Product.price}, never the legacy StoreProduct price.
 */
public record StoreAvailabilityResponse(Long storeProductId, Long storeId, String storeName, BigDecimal price,
                                        boolean inStock) {
}
