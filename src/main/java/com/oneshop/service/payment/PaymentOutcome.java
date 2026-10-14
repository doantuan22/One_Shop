package com.oneshop.service.payment;

/** What one reconciliation / simulation step did, for the UI and for tests. Every value is a final answer. */
public enum PaymentOutcome {
    /** verified collection before the deadline: Order CONFIRMED / PAID */
    CONFIRMED,
    /** verified FAILED: Order CANCELLED, stock restored */
    CANCELLED_PAYMENT_FAILED,
    /** nothing collected at the deadline: Order CANCELLED, stock restored */
    CANCELLED_TIMEOUT,
    /** money collected at or after the deadline: Order CANCELLED, stock restored, refund simulated */
    CANCELLED_LATE_PAYMENT,
    /** money verified on an already CANCELLED Order: recorded, refund simulated, Order untouched */
    LATE_PAYMENT_REFUNDED,
    /** the result is unknown inside the reconciliation window: nothing changes */
    RECONCILING,
    /** still unknown at the end of the window: Order NEEDS_ATTENTION, stock stays deducted */
    NEEDS_ATTENTION,
    /** the customer's submission was recorded as unconfirmed (UNKNOWN) */
    IN_DOUBT_RECORDED,
    /** no decision yet (before the deadline, nothing collected) */
    AWAITING,
    ALREADY_PAID,
    ALREADY_CANCELLED,
    /** not an ONLINE Order or not in a payment state */
    NOT_APPLICABLE
}
