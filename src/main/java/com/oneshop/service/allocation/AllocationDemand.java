package com.oneshop.service.allocation;

/** What the customer wants of one SKU: the checked-out cart line, never a StoreProduct. */
public record AllocationDemand(Long productId, int quantity) {
}
