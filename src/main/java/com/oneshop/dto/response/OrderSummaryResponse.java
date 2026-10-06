package com.oneshop.dto.response;

import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;

/** An Order of one Store, with its own fulfillment type, payment method and item snapshots (BR-07, BR-08). */
public record OrderSummaryResponse(Long orderId, Long storeId, String storeName, String storeAddress,
                                   FulfillmentType fulfillmentType, PaymentMethod paymentMethod,
                                   OrderPaymentStatus paymentStatus, OrderStatus orderStatus, String receiverName,
                                   String receiverPhone, String shippingAddress, BigDecimal totalAmount,
                                   List<OrderItemResponse> items) {
}
