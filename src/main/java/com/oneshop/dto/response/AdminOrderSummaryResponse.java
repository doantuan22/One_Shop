package com.oneshop.dto.response;

public record AdminOrderSummaryResponse(OrderViewResponse order, String customerName, String customerEmail) {}
