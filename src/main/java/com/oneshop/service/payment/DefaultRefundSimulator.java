package com.oneshop.service.payment;

import com.oneshop.entity.Payment;
import org.springframework.stereotype.Component;

/** The simulated refund always succeeds; no bank is contacted and no money moves. */
@Component
public class DefaultRefundSimulator implements RefundSimulator {

    @Override
    public void simulate(Payment collected) {
        // intentionally empty: "Hoàn tiền mô phỏng"
    }
}
