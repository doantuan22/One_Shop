package com.oneshop.dto.response;

import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DeliveryOrderResponse(Long orderId, Long storeId, String storeName, LocalDateTime createdAt,
                                    FulfillmentType fulfillmentType, PaymentMethod paymentMethod,
                                    OrderPaymentStatus paymentStatus, OrderStatus orderStatus,
                                    String receiverName, String receiverPhone, String shippingAddress,
                                    BigDecimal totalAmount, DeliveryAction nextAction) {
}
