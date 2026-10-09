package com.oneshop.service;

import com.oneshop.dto.response.*;
import java.util.List;

public interface CustomerOrderService {
    List<OrderViewResponse> getOrders(String customerEmail);
    OrderDetailResponse getOrder(String customerEmail, Long orderId);

    /** Idempotent cancellation of the authenticated customer's own unpaid, not-yet-prepared Order. */
    void cancelOrder(String customerEmail, Long orderId);
}
