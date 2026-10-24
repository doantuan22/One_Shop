package com.oneshop.service.payment;

import com.oneshop.entity.Order;
import com.oneshop.entity.Payment;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Gateway abstraction (DEC-21). The only implementation is the server-side mock; there is no bank integration. The
 * callers hold the Order write lock for every mutation, and no method calls an external system.
 */
public interface PaymentGateway {

    /** False in production: no simulated payment result can be produced. */
    boolean enabled();

    /** The gateway's answer about the money of the Order, derived from its ledger rows only. */
    GatewayVerification verify(Order order, List<Payment> attempts);

    /** The gateway collected the money at {@code at} (server time). */
    void capture(Payment attempt, LocalDateTime at);

    /** The gateway declined: nothing is collected, final. */
    void decline(Payment attempt);

    /** The customer submitted at {@code submittedAt} and the gateway cannot confirm the result. */
    void markInDoubt(Payment attempt, LocalDateTime submittedAt);

    /** Operator answer for an in-doubt attempt: money was collected (at the submission time) or it was not. */
    void settleInDoubt(Payment attempt, boolean collected);
}
