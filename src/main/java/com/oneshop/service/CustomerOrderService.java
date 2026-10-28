package com.oneshop.service;

import com.oneshop.dto.response.*;
import java.util.List;

public interface CustomerOrderService {
    List<OrderViewResponse> getOrders(String customerEmail);
    /** The product lines of each of the customer's Orders (by order id), for the order list. */
    java.util.Map<Long, OrderListInfo> getOrderItems(String customerEmail);
    OrderDetailResponse getOrder(String customerEmail, Long orderId);

    /**
     * What the customer sees as the order number: their own running count (first order = 1, then 2, 3...), keyed by
     * order id, not the database id. Display only; links and lookups keep using the real id.
     */
    default java.util.Map<Long, Integer> getOrderNumbers(String customerEmail) {
        List<OrderViewResponse> list = getOrders(customerEmail); // newest first, so the oldest order gets 1
        java.util.Map<Long, Integer> numbers = new java.util.HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            numbers.put(list.get(i).orderId(), list.size() - i);
        }
        return numbers;
    }

    /**
     * Idempotent cancellation of the authenticated customer's own unpaid, not-yet-prepared Order.
     *
     * @return what happened, so the caller can tell the customer when the simulated refund still has to be retried
     */
    com.oneshop.service.payment.CustomerOrderCancellationService.Result cancelOrder(String customerEmail, Long orderId);
}
