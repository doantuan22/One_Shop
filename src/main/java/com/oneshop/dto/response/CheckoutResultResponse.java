package com.oneshop.dto.response;

import com.oneshop.entity.CheckoutStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** One checkout: the session that groups the Orders created together, one Order per Store. */
public record CheckoutResultResponse(Long checkoutId, CheckoutStatus status, BigDecimal totalAmount,
                                     LocalDateTime createdAt, List<OrderSummaryResponse> orders) {
}
