package com.oneshop.service;

import com.oneshop.dto.response.*;
import java.util.List;

public interface CustomerOrderService {
    List<OrderViewResponse> getOrders(String customerEmail);
    /** The product lines of each of the customer's Orders (by order id), for the order list. */
    java.util.Map<Long, OrderListInfo> getOrderItems(String customerEmail);
    OrderDetailResponse getOrder(String customerEmail, Long orderId);

    /**
     * Idempotent cancellation of the authenticated customer's own unpaid, not-yet-prepared Order.
     *
     * @return what happened, so the caller can tell the customer when the simulated refund still has to be retried
     */
    com.oneshop.service.payment.CustomerOrderCancellationService.Result cancelOrder(String customerEmail, Long orderId);
}
