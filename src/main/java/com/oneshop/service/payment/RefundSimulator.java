package com.oneshop.service.payment;

import com.oneshop.entity.Payment;

/**
 * The simulated bank side of a refund. There is no real refund: the default implementation always answers "done".
 * It is a seam so that a failing simulation (and the safe retry) can be exercised.
 */
public interface RefundSimulator {

    /** @throws RuntimeException when the simulated refund fails; nothing is moved */
    void simulate(Payment collected);
}
