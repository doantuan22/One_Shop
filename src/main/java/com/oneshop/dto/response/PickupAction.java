package com.oneshop.dto.response;

import com.oneshop.entity.OrderStatus;

public enum PickupAction {
    PREPARE(OrderStatus.CONFIRMED, OrderStatus.PREPARING, "prepare", "Bắt đầu chuẩn bị"),
    READY(OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP, "ready", "Sẵn sàng nhận hàng"),
    COMPLETE(OrderStatus.READY_FOR_PICKUP, OrderStatus.COMPLETED, "complete", "Xác nhận khách đã nhận hàng");

    private final OrderStatus expectedStatus;
    private final OrderStatus targetStatus;
    private final String path;
    private final String label;
    PickupAction(OrderStatus expected, OrderStatus target, String path, String label) {
        this.expectedStatus = expected; this.targetStatus = target; this.path = path; this.label = label;
    }
    public OrderStatus getExpectedStatus() { return expectedStatus; }
    public OrderStatus getTargetStatus() { return targetStatus; }
    public String getPath() { return path; }
    public String getLabel() { return label; }
}
