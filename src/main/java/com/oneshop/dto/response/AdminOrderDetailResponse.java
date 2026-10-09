package com.oneshop.dto.response;

public record AdminOrderDetailResponse(OrderDetailResponse detail, Long checkoutId, Long customerId,
                                       String customerName, String customerEmail) {}
