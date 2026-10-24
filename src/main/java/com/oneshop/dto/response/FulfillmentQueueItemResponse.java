package com.oneshop.dto.response;

import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.OrderFulfillmentStatus;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.service.fulfillment.FulfillmentAction;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One row of the Staff fulfillment queue: the part of an Order that one Store prepares. Items shown belong to this
 * Fulfillment only; other Stores' parts of the same Order are never listed here. The pickup code is never part of a
 * Staff view.
 *
 * @param actions       what Staff may do now (empty when the step is blocked or the Fulfillment is finished)
 * @param blockedReason why work cannot start although the Fulfillment is open (payment gate), otherwise {@code null}
 */
public record FulfillmentQueueItemResponse(Long fulfillmentId, Long orderId, Long storeId, String storeName,
                                           FulfillmentType fulfillmentType, OrderFulfillmentStatus status,
                                           OrderStatus orderStatus, PaymentMethod paymentMethod,
                                           OrderPaymentStatus paymentStatus, String customerName,
                                           String receiverName, int itemCount, int totalQuantity,
                                           LocalDateTime assignedAt, List<FulfillmentAction> actions,
                                           String blockedReason) {

    public FulfillmentAction nextAction() {
        return actions.isEmpty() ? null : actions.get(0);
    }

    public boolean blocked() {
        return blockedReason != null;
    }
}
