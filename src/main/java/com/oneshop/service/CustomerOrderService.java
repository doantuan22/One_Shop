package com.oneshop.service;

import com.oneshop.dto.response.*;
import java.util.List;

public interface CustomerOrderService {
    List<OrderViewResponse> getOrders(String customerEmail);
    OrderDetailResponse getOrder(String customerEmail, Long orderId);
}
