package com.oneshop.service.payment;

/**
 * Asks the real payment provider what happened to an Order's payment and stores the answer as evidence on the
 * Payment rows. It never decides anything about the Order and it makes its HTTP calls outside any database lock;
 * the caller then lets {@link PaymentReconciliationService} decide from the stored evidence.
 *
 * <p>Whoever is about to <em>cancel for non-payment</em> (timeout scan, expired payment page, customer cancel) must
 * refresh first: stale evidence must never cancel an Order whose money is already at the bank.
 */
public interface PaymentEvidenceRefresher {

    /** @return false when the provider could not answer; the evidence is then unchanged and nothing should be decided */
    boolean refresh(Long orderId);
}
