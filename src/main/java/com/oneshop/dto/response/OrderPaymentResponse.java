package com.oneshop.dto.response;

import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.PaymentStatus;

import java.math.BigDecimal;
import java.util.List;

public record OrderPaymentResponse(Long orderId, Long checkoutId, String storeName, BigDecimal totalAmount,
                                   PaymentMethod paymentMethod, OrderPaymentStatus paymentStatus,
                                   OrderStatus orderStatus, List<PaymentResponse> attempts) {

    public boolean payable() {
        return paymentMethod == PaymentMethod.ONLINE && orderStatus == OrderStatus.PENDING_PAYMENT
                && paymentStatus == OrderPaymentStatus.UNPAID;
    }

    public boolean hasPendingAttempt() {
        return attempts.stream().anyMatch(payment -> payment.status() == PaymentStatus.PENDING);
    }
}
