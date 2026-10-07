package com.oneshop.service;

import com.oneshop.dto.response.*;
import com.oneshop.entity.Order;
import java.util.List;

public interface PickupFulfillmentService {
    List<OrderViewResponse> getPickupOrders(String staffEmail);
    OrderDetailResponse getPickupOrder(String staffEmail, Long orderId);
    /** View hint for an already authorized Order; no code or mutation is returned. */
    PickupAction getNextAction(Order order);
    void startPreparingPickup(String staffEmail, Long orderId);
    void markReadyForPickup(String staffEmail, Long orderId);
    void completePickup(String staffEmail, Long orderId, String pickupCode);
}
