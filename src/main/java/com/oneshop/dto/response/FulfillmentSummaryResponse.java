package com.oneshop.dto.response;

import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.OrderFulfillmentStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The part of an Order that one Store prepares and delivers. It is not an Order of its own: it has no payment and no
 * total, and it is shown inside the Order it belongs to.
 *
 * @param trackingCode the carrier hand-over reference Staff entered, or {@code null}
 * @param pickupCode   shown only to the customer who owns the Order while the goods wait at the Store, else {@code null}
 */
public record FulfillmentSummaryResponse(Long fulfillmentId, Long storeId, String storeName, String storeAddress,
                                         FulfillmentType fulfillmentType, OrderFulfillmentStatus status,
                                         List<OrderItemResponse> items, String trackingCode, String pickupCode,
                                         LocalDateTime readyAt, LocalDateTime completedAt) {

    /** Checkout result shape: no tracking, code or timestamps yet. */
    public FulfillmentSummaryResponse(Long fulfillmentId, Long storeId, String storeName, String storeAddress,
                                      FulfillmentType fulfillmentType, OrderFulfillmentStatus status,
                                      List<OrderItemResponse> items) {
        this(fulfillmentId, storeId, storeName, storeAddress, fulfillmentType, status, items, null, null, null, null);
    }
}
