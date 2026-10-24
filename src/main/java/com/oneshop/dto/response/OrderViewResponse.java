package com.oneshop.dto.response;

import com.oneshop.entity.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Detached view; customer-only code is deliberately absent in staff views and list views. */
public record OrderViewResponse(Long orderId, Long storeId, String storeName, String storeAddress,
        LocalDateTime createdAt, FulfillmentType fulfillmentType, PaymentMethod paymentMethod,
        OrderPaymentStatus paymentStatus, OrderStatus orderStatus, String receiverName, String receiverPhone,
        String shippingAddress, BigDecimal totalAmount, LocalDateTime readyAt, LocalDateTime pickedUpAt,
        String pickupCode, PickupAction nextAction, BigDecimal shippingFee, BigDecimal discountAmount,
        String promotionCode) {

    /** The view without the money breakdown (shipping fee, promotion): both read as "none". */
    public OrderViewResponse(Long orderId, Long storeId, String storeName, String storeAddress,
            LocalDateTime createdAt, FulfillmentType fulfillmentType, PaymentMethod paymentMethod,
            OrderPaymentStatus paymentStatus, OrderStatus orderStatus, String receiverName, String receiverPhone,
            String shippingAddress, BigDecimal totalAmount, LocalDateTime readyAt, LocalDateTime pickedUpAt,
            String pickupCode, PickupAction nextAction) {
        this(orderId, storeId, storeName, storeAddress, createdAt, fulfillmentType, paymentMethod, paymentStatus,
                orderStatus, receiverName, receiverPhone, shippingAddress, totalAmount, readyAt, pickedUpAt, pickupCode,
                nextAction, BigDecimal.ZERO, BigDecimal.ZERO, null);
    }

    /** True when a promotion code took something off the goods of this Order. */
    public boolean hasDiscount() {
        return discountAmount != null && discountAmount.signum() > 0;
    }

    /** Goods before the discount: {@code total - shipping fee + discount}. */
    public BigDecimal goodsAmount() {
        BigDecimal fee = shippingFee == null ? BigDecimal.ZERO : shippingFee;
        BigDecimal discount = discountAmount == null ? BigDecimal.ZERO : discountAmount;
        return totalAmount.subtract(fee).add(discount);
    }
}
