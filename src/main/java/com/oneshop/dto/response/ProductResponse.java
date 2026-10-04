package com.oneshop.dto.response;

/** Chain-wide view of a SKU. No price or stock: those are per Store (see StoreAvailabilityResponse). */
public record ProductResponse(Long id, String sku, String name, String description, String categoryName,
                              String brandName, String imageUrl) {
}
