package com.oneshop.dto.response;

import com.oneshop.entity.OrderStatus;

/** Fixed Staff operations, never bound from a request or persisted as an Order status. */
public enum DeliveryAction {
    PREPARE(OrderStatus.CONFIRMED, OrderStatus.PREPARING, "prepare", "Bắt đầu chuẩn bị"),
    PACK(OrderStatus.PREPARING, OrderStatus.PACKED, "pack", "Đã đóng gói"),
    SHIP(OrderStatus.PACKED, OrderStatus.SHIPPING, "ship", "Bắt đầu giao hàng"),
    COMPLETE(OrderStatus.SHIPPING, OrderStatus.COMPLETED, "complete", "Xác nhận giao thành công");

    private final OrderStatus expectedStatus;
    private final OrderStatus targetStatus;
    private final String path;
    private final String label;

    DeliveryAction(OrderStatus expectedStatus, OrderStatus targetStatus, String path, String label) {
        this.expectedStatus = expectedStatus;
        this.targetStatus = targetStatus;
        this.path = path;
        this.label = label;
    }

    public OrderStatus getExpectedStatus() { return expectedStatus; }
    public OrderStatus getTargetStatus() { return targetStatus; }
    public String getPath() { return path; }
    public String getLabel() { return label; }
}
