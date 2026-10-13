package com.oneshop.service.allocation;

import java.util.List;

/** The part of a cart one Store supplies: becomes one OrderFulfillment. */
public record StoreAllocation(Long storeId, List<AllocatedLine> lines) {
}
