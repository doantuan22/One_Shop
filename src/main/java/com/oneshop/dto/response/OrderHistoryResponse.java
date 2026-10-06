package com.oneshop.dto.response;

import com.oneshop.entity.OrderStatus;

import java.time.LocalDateTime;

public record OrderHistoryResponse(OrderStatus oldStatus, OrderStatus newStatus, String actorName,
                                   String note, LocalDateTime changedAt) {
}
