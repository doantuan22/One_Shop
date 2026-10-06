package com.oneshop.service;

import com.oneshop.dto.response.DeliveryOrderDetailResponse;
import com.oneshop.dto.response.DeliveryOrderResponse;

import java.util.List;

/** DELIVERY orchestration; staffEmail must come from the authenticated principal. */
public interface DeliveryFulfillmentService {
    List<DeliveryOrderResponse> getDeliveryOrders(String staffEmail);
    DeliveryOrderDetailResponse getDeliveryOrder(String staffEmail, Long orderId);
    void startPreparingDelivery(String staffEmail, Long orderId);
    void markDeliveryPacked(String staffEmail, Long orderId);
    void markDeliveryShipping(String staffEmail, Long orderId);
    void completeDelivery(String staffEmail, Long orderId);
}
