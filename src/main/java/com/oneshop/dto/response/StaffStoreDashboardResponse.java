package com.oneshop.dto.response;

/**
 * Counts of the Fulfillments of one assigned Store, calculated in SQL from {@code order_fulfillments.store_id}.
 *
 * @param openFulfillmentCount  Fulfillments Staff can work on now (not finished, not held by the payment gate)
 * @param waitingPickupCount    pickup Fulfillments ready and waiting for the customer
 * @param shippingCount         delivery Fulfillments handed to the carrier
 * @param blockedCount          open Fulfillments held back by the Order (PENDING_PAYMENT / NEEDS_ATTENTION / CANCELLED)
 * @param failedCount           Fulfillments marked FAILED, waiting for Admin handling
 */
public record StaffStoreDashboardResponse(StoreResponse store, long openFulfillmentCount, long waitingPickupCount,
                                          long lowStockSkuCount, long outOfStockSkuCount, long shippingCount,
                                          long blockedCount, long failedCount) {

    /** Shape used before BE-04 (Order counts); the new counters are zero. */
    public StaffStoreDashboardResponse(StoreResponse store, long pendingOrderCount, long waitingPickupCount,
                                       long lowStockSkuCount, long outOfStockSkuCount) {
        this(store, pendingOrderCount, waitingPickupCount, lowStockSkuCount, outOfStockSkuCount, 0, 0, 0);
    }

    /** Name used by the V2 dashboard for {@link #openFulfillmentCount()}. */
    public long pendingOrderCount() {
        return openFulfillmentCount;
    }
}
