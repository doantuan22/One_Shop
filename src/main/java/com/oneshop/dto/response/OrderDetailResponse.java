package com.oneshop.dto.response;

import java.util.List;

public record OrderDetailResponse(OrderViewResponse order, List<OrderItemResponse> items,
        List<OrderHistoryResponse> history, List<PaymentResponse> payments) {}
