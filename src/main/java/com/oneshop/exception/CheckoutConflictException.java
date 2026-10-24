package com.oneshop.exception;

/**
 * Checkout could not continue because what the customer confirmed no longer matches the current data (plan, total,
 * missing split consent) or because a flow is closed pending a business decision. It is a business refusal: nothing
 * was created, no stock moved, and the page shows the current preview so the customer can confirm again.
 */
public class CheckoutConflictException extends BadRequestException {

    public static final String CODE_PLAN_CHANGED = "PLAN_CHANGED";
    public static final String CODE_TOTAL_CHANGED = "TOTAL_CHANGED";
    public static final String CODE_COD_SPLIT_CONSENT_REQUIRED = "COD_SPLIT_CONSENT_REQUIRED";
    public static final String CODE_DELIVERY_POLICY_PENDING = "DELIVERY_POLICY_PENDING";
    public static final String CODE_ONLINE_CHECKOUT_DISABLED = "ONLINE_CHECKOUT_DISABLED";
    public static final String CODE_NOT_ALLOCATABLE = "NOT_ALLOCATABLE";

    private final String code;

    public CheckoutConflictException(String code, String message) {
        super("checkout", message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
