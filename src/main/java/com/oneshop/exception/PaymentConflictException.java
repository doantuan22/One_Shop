package com.oneshop.exception;

/**
 * A payment, cancellation or reconciliation request was refused because of the current payment state of the Order
 * (still being reconciled, already settled, gateway disabled, expired). Nothing was changed. Handlers answer 409 with
 * the stable {@link #getCode() code}.
 */
public class PaymentConflictException extends BadRequestException {

    public static final String PAYMENT_RECONCILING = "PAYMENT_RECONCILING";
    public static final String PAYMENT_NOT_PAYABLE = "PAYMENT_NOT_PAYABLE";
    public static final String PAYMENT_ALREADY_FINAL = "PAYMENT_ALREADY_FINAL";
    public static final String GATEWAY_DISABLED = "GATEWAY_DISABLED";
    public static final String NOT_A_PAYMENT_CASE = "NOT_A_PAYMENT_CASE";
    public static final String NOTHING_TO_REFUND = "NOTHING_TO_REFUND";

    private final String code;

    public PaymentConflictException(String code, String message) {
        super("payment", message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
