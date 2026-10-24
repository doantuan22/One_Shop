package com.oneshop.service;

import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.Store;
import com.oneshop.service.allocation.AllocationDemand;
import com.oneshop.service.allocation.AllocationResult;
import com.oneshop.service.allocation.LockedAllocation;

import java.util.Collection;
import java.util.List;

/**
 * Centralized Store Allocation (V3 DEC-05): decides which Store supplies which SKU of a cart. The customer never
 * chooses a Store for DELIVERY; for STORE_PICKUP the chosen Store must supply the whole cart.
 *
 * <p>Eligible sources are ACTIVE StoreProducts of ACTIVE Stores of visible Products, with
 * {@code quantity - reserved_quantity} available, at Stores that support the fulfillment type and (DELIVERY) serve
 * the address under the approved service-area policy. Chain-wide totals are never evidence that one Store is enough.
 * Stock is never transferred between Stores.
 */
public interface StoreAllocationService {

    /**
     * Read-only allocation on current, unlocked data: for a preview. It does not reserve anything and the result may
     * be outdated by the time the order is placed.
     */
    AllocationResult preview(Collection<AllocationDemand> demands, FulfillmentType type, Long pickupStoreId,
                             String shippingAddress);

    /**
     * Stores where the whole cart can be collected right now: ACTIVE, pickup enabled, every SKU in full. Read-only
     * and unlocked, for the pickup choice on the checkout page; the choice is validated again when ordering.
     */
    List<Store> pickupStoresCovering(Collection<AllocationDemand> demands);

    /**
     * Allocation for placing an order. Must run inside a write transaction: it locks every candidate StoreProduct of
     * the demanded SKUs in ascending id order, reads quantity/status under that lock and allocates on those rows.
     * Nothing is written.
     */
    LockedAllocation allocateForUpdate(Collection<AllocationDemand> demands, FulfillmentType type, Long pickupStoreId,
                                       String shippingAddress);
}
