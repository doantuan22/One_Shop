package com.oneshop.entity;

/**
 * Values of {@code payments.refund_status} and {@code payment_refunds.status}.
 *
 * <p>The first three belong to the simulated gateway and never mean real money moved. {@link #MANUAL_REQUESTED} and
 * {@link #REFUNDED} belong to real (payOS) payments: a refund is only {@code REFUNDED} after an administrator has
 * recorded the bank transaction that really gave the money back.
 */
public enum PaymentRefundStatus {
    NONE,
    PENDING_SIMULATION,
    SIMULATED_REFUNDED,
    SIMULATION_FAILED,
    /** a real refund is owed and waits for an administrator to pay it out of the bank and record the evidence */
    MANUAL_REQUESTED,
    /** the real refund was paid and confirmed with a bank transaction code */
    REFUNDED;

    /** True for the states in which a real refund has been asked for or finished. */
    public boolean isReal() {
        return this == MANUAL_REQUESTED || this == REFUNDED;
    }
}
