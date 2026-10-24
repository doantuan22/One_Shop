package com.oneshop.dto.response;

import com.oneshop.entity.OrderFulfillmentStatus;

import java.time.LocalDateTime;

/** One transition of a Fulfillment (order_fulfillment_status_history). */
public record FulfillmentHistoryResponse(OrderFulfillmentStatus oldStatus, OrderFulfillmentStatus newStatus,
                                         String actorName, String note, LocalDateTime changedAt) {
}
