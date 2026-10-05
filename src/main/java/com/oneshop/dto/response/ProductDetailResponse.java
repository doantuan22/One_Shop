package com.oneshop.dto.response;

import java.util.List;

/**
 * Product Detail of the Client (Roadmap V2 9.1). There is never an exact quantity in it (BR-15).
 *
 * @param atSelectedStore price and availability at the selected Store; {@code null} when no Store is selected or the
 *                        selected Store does not sell this SKU
 * @param otherStores     the other Stores selling the SKU (all of them when no Store is selected)
 */
public record ProductDetailResponse(ProductResponse product, List<ProductImageResponse> images,
                                    StoreAvailabilityResponse atSelectedStore,
                                    List<StoreAvailabilityResponse> otherStores) {
}
