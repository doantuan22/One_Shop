package com.oneshop.dto.response;

import com.oneshop.entity.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Detached view; customer-only code is deliberately absent in staff views and list views. */
public record OrderViewResponse(Long orderId, Long storeId, String storeName, String storeAddress,
        LocalDateTime createdAt, FulfillmentType fulfillmentType, PaymentMethod paymentMethod,
        OrderPaymentStatus paymentStatus, OrderStatus orderStatus, String receiverName, String receiverPhone,
        String shippingAddress, BigDecimal totalAmount, LocalDateTime readyAt, LocalDateTime pickedUpAt,
        String pickupCode, PickupAction nextAction) {}
