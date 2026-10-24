package com.oneshop.service.fulfillment;

import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.OrderFulfillmentStatus;

/**
 * The only operations Staff may apply to one Fulfillment (V3-BE-04). Each one names the Fulfillment type it is for, the
 * status it expects and the status it produces; nothing else moves a Fulfillment, and no operation touches stock.
 *
 * <p>DELIVERY: {@code CONFIRMED → PREPARING → PACKED → SHIPPING → COMPLETED | FAILED}.
 * STORE_PICKUP: {@code CONFIRMED → PREPARING → READY_FOR_PICKUP → COMPLETED | FAILED}.
 */
public enum FulfillmentAction {
    PREPARE("prepare", null, OrderFulfillmentStatus.CONFIRMED, OrderFulfillmentStatus.PREPARING, "Bắt đầu chuẩn bị"),
    PACK("pack", FulfillmentType.DELIVERY, OrderFulfillmentStatus.PREPARING, OrderFulfillmentStatus.PACKED, "Đã đóng gói"),
    SHIP("ship", FulfillmentType.DELIVERY, OrderFulfillmentStatus.PACKED, OrderFulfillmentStatus.SHIPPING, "Bàn giao vận chuyển"),
    MARK_READY("ready-for-pickup", FulfillmentType.STORE_PICKUP, OrderFulfillmentStatus.PREPARING, OrderFulfillmentStatus.READY_FOR_PICKUP,
            "Sẵn sàng cho khách nhận"),
    COMPLETE_DELIVERY("complete-delivery", FulfillmentType.DELIVERY, OrderFulfillmentStatus.SHIPPING, OrderFulfillmentStatus.COMPLETED,
            "Xác nhận giao thành công"),
    COMPLETE_PICKUP("complete-pickup", FulfillmentType.STORE_PICKUP, OrderFulfillmentStatus.READY_FOR_PICKUP,
            OrderFulfillmentStatus.COMPLETED, "Xác nhận khách đã nhận hàng"),
    FAIL_DELIVERY("fail-delivery", FulfillmentType.DELIVERY, OrderFulfillmentStatus.SHIPPING, OrderFulfillmentStatus.FAILED,
            "Giao hàng không thành công"),
    FAIL_PICKUP("fail-pickup", FulfillmentType.STORE_PICKUP, OrderFulfillmentStatus.READY_FOR_PICKUP, OrderFulfillmentStatus.FAILED,
            "Khách không nhận hàng");

    private final String path;
    private final FulfillmentType type;
    private final OrderFulfillmentStatus from;
    private final OrderFulfillmentStatus to;
    private final String label;

    FulfillmentAction(String path, FulfillmentType type, OrderFulfillmentStatus from, OrderFulfillmentStatus to,
                      String label) {
        this.path = path;
        this.type = type;
        this.from = from;
        this.to = to;
        this.label = label;
    }

    /** The URL segment of the POST that applies this action. */
    public String getPath() {
        return path;
    }

    /** The action behind a URL segment, or {@code null} for an unknown one. */
    public static FulfillmentAction fromPath(String path) {
        for (FulfillmentAction action : values()) {
            if (action.path.equals(path)) {
                return action;
            }
        }
        return null;
    }

    /** {@code null} means both types. */
    public FulfillmentType getType() {
        return type;
    }

    public OrderFulfillmentStatus getFrom() {
        return from;
    }

    public OrderFulfillmentStatus getTo() {
        return to;
    }

    public String getLabel() {
        return label;
    }

    public boolean appliesTo(FulfillmentType fulfillmentType) {
        return type == null || type == fulfillmentType;
    }

    /** A failure needs a written reason; it never cancels, restocks or refunds. */
    public boolean isFailure() {
        return to == OrderFulfillmentStatus.FAILED;
    }

    public boolean isCompletion() {
        return to == OrderFulfillmentStatus.COMPLETED;
    }
}
