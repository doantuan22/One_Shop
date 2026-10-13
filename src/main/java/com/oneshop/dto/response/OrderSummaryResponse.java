package com.oneshop.dto.response;

import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * An Order with its own fulfillment type, payment method and item snapshots. In V3 an ONLINE Order may be supplied by
 * several Stores: {@code storeId} is then {@code null}, {@code storeName} names the number of Stores, and
 * {@code fulfillments} lists the part of each Store. Fulfillments are not Orders.
 *
 * @param paymentDeadline end of the ONLINE payment window ({@code created_at + 15 minutes}), or {@code null}
 */
public record OrderSummaryResponse(Long orderId, Long storeId, String storeName, String storeAddress,
                                   FulfillmentType fulfillmentType, PaymentMethod paymentMethod,
                                   OrderPaymentStatus paymentStatus, OrderStatus orderStatus, String receiverName,
                                   String receiverPhone, String shippingAddress, BigDecimal totalAmount,
                                   List<OrderItemResponse> items, List<FulfillmentSummaryResponse> fulfillments,
                                   LocalDateTime paymentDeadline, BigDecimal shippingFee) {

    /** Legacy shape without Fulfillments, deadline and shipping fee, kept for V2 callers and tests. */
    public OrderSummaryResponse(Long orderId, Long storeId, String storeName, String storeAddress,
                                FulfillmentType fulfillmentType, PaymentMethod paymentMethod,
                                OrderPaymentStatus paymentStatus, OrderStatus orderStatus, String receiverName,
                                String receiverPhone, String shippingAddress, BigDecimal totalAmount,
                                List<OrderItemResponse> items) {
        this(orderId, storeId, storeName, storeAddress, fulfillmentType, paymentMethod, paymentStatus, orderStatus,
                receiverName, receiverPhone, shippingAddress, totalAmount, items, List.of(), null, null);
    }
}
