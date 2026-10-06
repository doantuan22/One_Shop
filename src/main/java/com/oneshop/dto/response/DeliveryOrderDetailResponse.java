package com.oneshop.dto.response;

import java.util.List;

/** Detached view data: snapshots and audit, with no editable fields. */
public record DeliveryOrderDetailResponse(DeliveryOrderResponse order, List<OrderItemResponse> items,
                                          List<OrderHistoryResponse> history, List<PaymentResponse> payments) {
}
