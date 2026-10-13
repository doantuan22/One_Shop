package com.oneshop.service.fulfillment;

import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.OrderFulfillmentStatus;

import java.time.LocalDate;

/**
 * Optional filters of the Staff queue. {@code storeId} narrows the assigned Stores, it never widens them: a Store
 * outside the assignments is refused, not ignored. All fields are optional.
 *
 * @param openOnly only unfinished Fulfillments (CONFIRMED, PREPARING, PACKED, SHIPPING, READY_FOR_PICKUP)
 */
public record FulfillmentQueueFilter(Long storeId, OrderFulfillmentStatus status, FulfillmentType type,
                                     Long orderId, LocalDate from, LocalDate to, boolean openOnly) {

    public static FulfillmentQueueFilter none() {
        return new FulfillmentQueueFilter(null, null, null, null, null, null, false);
    }

    public static FulfillmentQueueFilter openWork() {
        return new FulfillmentQueueFilter(null, null, null, null, null, null, true);
    }
}
