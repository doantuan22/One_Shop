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

    /** Single online price of the SKU in V3. Never substitute a StoreProduct's legacy price. */
    public BigDecimal lowestPrice() {
        return product == null ? null : product.price();
    }

    public boolean samePriceEverywhere() {
        return true;
    }
}
