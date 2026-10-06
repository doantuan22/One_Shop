package com.oneshop.service;

import com.oneshop.dto.response.*;
import java.util.List;

public interface PickupFulfillmentService {
    List<OrderViewResponse> getPickupOrders(String staffEmail);
    OrderDetailResponse getPickupOrder(String staffEmail, Long orderId);
    void startPreparingPickup(String staffEmail, Long orderId);
    void markReadyForPickup(String staffEmail, Long orderId);
    void completePickup(String staffEmail, Long orderId, String pickupCode);
}
