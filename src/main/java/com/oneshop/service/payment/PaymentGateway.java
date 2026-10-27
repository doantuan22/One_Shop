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

    /** Which provider this gateway speaks for: the {@code payments.provider} it writes and reads. */
    default com.oneshop.entity.PaymentProvider provider() {
        return com.oneshop.entity.PaymentProvider.MOCK;
    }

    /** True while ONLINE checkout can really be settled by this gateway (configured and permitted). */
    default boolean onlineAvailable() {
        return enabled();
    }

    /**
     * The verified collection is applied to the attempt: {@code SUCCESS} at {@code at}. Called by the reconciliation
     * engine under the Order lock, only for the attempt the verification named.
     */
    void applyCollected(Payment attempt, LocalDateTime at);

    /** True only for the simulated gateway while it is permitted: false in production and whenever real money is used. */
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
