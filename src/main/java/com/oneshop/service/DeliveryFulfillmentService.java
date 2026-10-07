package com.oneshop.service;

import com.oneshop.dto.response.DeliveryOrderDetailResponse;
import com.oneshop.dto.response.DeliveryOrderResponse;
import com.oneshop.dto.response.DeliveryAction;
import com.oneshop.entity.Order;

import java.util.List;

/** DELIVERY orchestration; staffEmail must come from the authenticated principal. */
public interface DeliveryFulfillmentService {
    List<DeliveryOrderResponse> getDeliveryOrders(String staffEmail);
    DeliveryOrderDetailResponse getDeliveryOrder(String staffEmail, Long orderId);
    /** View hint for an already authorized Order; mutations independently recheck all rules. */
    DeliveryAction getNextAction(Order order);
    void startPreparingDelivery(String staffEmail, Long orderId);
    void markDeliveryPacked(String staffEmail, Long orderId);
    void markDeliveryShipping(String staffEmail, Long orderId);
    void completeDelivery(String staffEmail, Long orderId);
}
