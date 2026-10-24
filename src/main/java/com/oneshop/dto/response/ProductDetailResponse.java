package com.oneshop.dto.response;

import java.util.List;

/**
 * V3 product detail: one shared online Product.price with Store availability, never a customer-visible quantity.
 *
 * @param atSelectedStore optional read-only legacy Store lookup; never sets the shared online price
 * @param otherStores     the other Stores selling the SKU (all of them when no Store is selected)
 */
public record ProductDetailResponse(ProductResponse product, List<ProductImageResponse> images,
                                    StoreAvailabilityResponse atSelectedStore,
                                    List<StoreAvailabilityResponse> otherStores) {

    /** Returns true if in stock at the selected store or at any store across the chain. */
    public boolean inStock() {
        if (atSelectedStore != null && atSelectedStore.inStock()) {
            return true;
        }
        return otherStores != null && otherStores.stream().anyMatch(StoreAvailabilityResponse::inStock);
    }

    public int inStockStoreCount() {
        int count = 0;
        if (atSelectedStore != null && atSelectedStore.inStock()) {
            count++;
        }
        if (otherStores != null) {
            count += (int) otherStores.stream().filter(StoreAvailabilityResponse::inStock).count();
        }
        return count;
    }
}
