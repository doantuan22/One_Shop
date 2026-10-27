package com.oneshop.service.payment;

import com.oneshop.entity.Payment;
import org.springframework.stereotype.Component;

/** The simulated refund always succeeds; no bank is contacted and no money moves. */
@Component
public class DefaultRefundSimulator implements RefundSimulator {

    @Override
    public void simulate(Payment collected) {
        if (collected.isPayos()) {
            // a simulation must never stand in for a real refund of real money
            throw new IllegalStateException("Khoản thanh toán payOS không thể hoàn tiền mô phỏng.");
        }
        // intentionally empty: "Hoàn tiền mô phỏng"
    }
}
