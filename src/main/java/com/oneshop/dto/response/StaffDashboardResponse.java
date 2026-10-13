package com.oneshop.dto.response;

import java.util.List;

/** Staff dashboard: per Store Fulfillment counters plus the latest Fulfillments of the assigned Stores. */
public record StaffDashboardResponse(List<StaffStoreDashboardResponse> stores, int lowStockThreshold,
                                     List<FulfillmentQueueItemResponse> recentFulfillments) {

    /** Name used by the V2 dashboard for {@link #recentFulfillments()}. */
    public List<FulfillmentQueueItemResponse> recentOrders() {
        return recentFulfillments;
    }
}
