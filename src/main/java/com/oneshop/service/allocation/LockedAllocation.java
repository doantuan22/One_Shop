package com.oneshop.service.allocation;

import com.oneshop.entity.StoreProduct;

import java.util.Map;

/**
 * An allocation computed on StoreProduct rows that are locked (PESSIMISTIC_WRITE) in the current transaction, with
 * those rows, so the caller deducts exactly what was verified under the lock.
 *
 * @param lockedStoreProducts every locked candidate, by StoreProduct id
 */
public record LockedAllocation(AllocationResult result, Map<Long, StoreProduct> lockedStoreProducts) {
}
