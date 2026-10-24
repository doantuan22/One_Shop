package com.oneshop.service.payment;

/**
 * State of one ONLINE attempt in the server-side mock gateway ledger (DEC-21). The ledger is not a table of its own:
 * it is read from, and written to, the {@code payments} row of the attempt (see {@link MockLedger}).
 */
public enum LedgerState {
    /** Created by checkout; the customer has not completed any payment. */
    AWAITING,
    /** The customer submitted a payment and the gateway could not confirm it (UNKNOWN). Money may have moved. */
    IN_DOUBT,
    /** The gateway reports money collected at a server time; the Order has not been told yet. */
    CAPTURED,
    /** The gateway reports that nothing was collected (verified FAILED, or the attempt was closed unpaid). */
    DECLINED,
    /** The collection was applied to the Order ({@code payments.status = SUCCESS}). */
    SETTLED
}
