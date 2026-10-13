package com.oneshop.dto.response;

import java.util.List;

/**
 * Order detail. {@code fulfillments} lists the parts of the Order per Store (V3); it is empty only for shapes built
 * before BE-04.
 */
public record OrderDetailResponse(OrderViewResponse order, List<OrderItemResponse> items,
                                  List<OrderHistoryResponse> history, List<PaymentResponse> payments,
                                  List<FulfillmentSummaryResponse> fulfillments) {

    public OrderDetailResponse(OrderViewResponse order, List<OrderItemResponse> items,
                               List<OrderHistoryResponse> history, List<PaymentResponse> payments) {
        this(order, items, history, payments, List.of());
    }
}
