package com.oneshop.dto.response;

public record StaffOrderDetailResponse(OrderDetailResponse detail, String customerName,
                                      DeliveryAction deliveryAction, PickupAction pickupAction) { }
