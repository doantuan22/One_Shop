package com.oneshop.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Whether the Customer may ask to return an Order now.
 *
 * @param allowed      the request form may be shown and submitted
 * @param message      why not (Vietnamese, safe for the Customer); {@code null} when allowed
 * @param receivedAt   completion time of the latest Fulfillment (start of the 3-day window); {@code null} when unknown
 * @param deadline     last moment a request is accepted; {@code null} when unknown
 * @param refundAmount goods value that would be refunded (the shipping fee is kept); {@code null} when unknown
 */
public record ReturnEligibilityResponse(boolean allowed, String message, LocalDateTime receivedAt,
                                        LocalDateTime deadline, BigDecimal refundAmount) {

    public static ReturnEligibilityResponse blocked(String message) {
        return new ReturnEligibilityResponse(false, message, null, null, null);
    }
}
