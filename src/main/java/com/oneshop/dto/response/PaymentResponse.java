package com.oneshop.dto.response;

import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(Long paymentId, Long orderId, PaymentMethod method, BigDecimal amount,
                              PaymentStatus status, String transactionCode, LocalDateTime paidAt,
                              LocalDateTime createdAt) {
}
