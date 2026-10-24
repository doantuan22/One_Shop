package com.oneshop.dto.response;

import com.oneshop.entity.OrderFulfillmentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Staff detail of one Fulfillment. Only the Store's own lines are listed. The receiver and address are shown only
 * because this Store has to hand the parts over; the pickup code itself is never exposed to Staff, only whether one
 * has been issued.
 *
 * @param amountToCollect the amount Staff must collect when finishing this step (COD / PAY_AT_STORE), else {@code null}
 * @param statusHistory   transitions of this Fulfillment, oldest first
 */
public record FulfillmentDetailResponse(FulfillmentQueueItemResponse summary, String receiverPhone,
                                        String shippingAddress, String trackingCode, boolean pickupCodeIssued,
                                        BigDecimal itemsSubtotal, BigDecimal orderTotal, BigDecimal amountToCollect,
                                        String note, String cancelReason, LocalDateTime preparedAt,
                                        LocalDateTime packedAt, LocalDateTime shippedAt, LocalDateTime readyAt,
                                        LocalDateTime completedAt, List<OrderItemResponse> items,
                                        List<FulfillmentHistoryResponse> statusHistory) {

    public OrderFulfillmentStatus status() {
        return summary.status();
    }
}
