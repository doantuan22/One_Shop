package com.oneshop.exception;

/**
 * A fulfillment operation was refused because of the current state of the Order or Fulfillment (already prepared,
 * cancelled, payment not confirmed, wrong status, pickup code locked). It is a business refusal: nothing was changed.
 * Web and API handlers answer it with HTTP 409 and the stable {@link #getCode() code}.
 */
public class FulfillmentConflictException extends BadRequestException {

    public static final String STATUS_CHANGED = "FULFILLMENT_STATUS_CHANGED";
    public static final String PAYMENT_PENDING = "PAYMENT_PENDING";
    public static final String PAYMENT_NOT_VERIFIED = "PAYMENT_NOT_VERIFIED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String ORDER_NEEDS_ATTENTION = "ORDER_NEEDS_ATTENTION";
    public static final String ORDER_ALREADY_PREPARING = "ORDER_ALREADY_PREPARING";
    public static final String PICKUP_CODE_LOCKED = "PICKUP_CODE_LOCKED";
    public static final String COLLECTION_BLOCKED = "COLLECTION_BLOCKED";
    public static final String INCONSISTENT_FULFILLMENT = "INCONSISTENT_FULFILLMENT";
    /** a return request was already approved or rejected */
    public static final String RETURN_ALREADY_DECIDED = "RETURN_ALREADY_DECIDED";
    /** a returned line was already restocked or disposed of */
    public static final String RETURN_ALREADY_HANDLED = "RETURN_ALREADY_HANDLED";

    private final String code;

    public FulfillmentConflictException(String code, String message) {
        super("fulfillment", message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
