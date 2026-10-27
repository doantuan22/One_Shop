package com.oneshop.service.payment;

import java.time.LocalDateTime;

/**
 * What the gateway answers about the money of one Order: never the browser's claim.
 *
 * @param reference  gateway reference the answer is about (may be {@code null} when there is no attempt)
 * @param paymentId  the {@code payments} row the answer is about when the provider names one (payOS); else {@code null}
 * @param capturedAt server time of the collection for {@link Result#SUCCESS}; the submission time for
 *                   {@link Result#UNKNOWN} when known; otherwise {@code null}
 */
public record GatewayVerification(Result result, String reference, LocalDateTime capturedAt, Long paymentId) {

    public GatewayVerification(Result result, String reference, LocalDateTime capturedAt) {
        this(result, reference, capturedAt, null);
    }


    public enum Result {
        /** money collected at {@code capturedAt} */
        SUCCESS,
        /** verified declined: nothing collected, final */
        FAILED,
        /** nothing was collected and nothing is in flight (the customer never completed a payment) */
        NOT_PAID,
        /** the gateway cannot say (no answer, or the customer's submission is unconfirmed) */
        UNKNOWN
    }

    public static GatewayVerification unknown(String reference) {
        return new GatewayVerification(Result.UNKNOWN, reference, null);
    }

    public boolean conclusive() {
        return result != Result.UNKNOWN;
    }
}
