package com.oneshop.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * One product card of the Client catalog. There is never an exact quantity in it (BR-15).
 *
 * @param atSelectedStore price and availability at the selected Store; {@code null} when browsing the whole chain
 * @param stores          chain-wide view: every Store currently selling the SKU; empty in the context of one Store
 */
public record CatalogItemResponse(ProductResponse product, StoreAvailabilityResponse atSelectedStore,
                                  List<StoreAvailabilityResponse> stores) {

    /** Unified online price of the SKU in V3, or lowest price if legacy. */
    public BigDecimal lowestPrice() {
        if (product != null && product.price() != null) {
            return product.price();
        }
        return stores.stream().map(StoreAvailabilityResponse::price).min(BigDecimal::compareTo).orElse(null);
    }

    public boolean samePriceEverywhere() {
        return true;
    }
}
