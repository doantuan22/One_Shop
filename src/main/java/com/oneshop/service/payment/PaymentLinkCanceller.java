package com.oneshop.service.payment;

import com.oneshop.entity.Payment;

/**
 * Closes a payment link at the provider once the Order is cancelled, so the customer cannot keep paying a dead Order.
 * Closing a link is NOT a refund and is never evidence that no money moved: a transfer that already happened is
 * found later by reconciliation and refunded through the refund workflow.
 */
public interface PaymentLinkCanceller {

    /** Runs after the surrounding transaction commits (or at once outside one). Never throws. */
    void cancelAfterCommit(Payment attempt);
}
