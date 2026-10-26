package com.oneshop.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Says once at startup whether the payment timeout worker (a writer) is running, so nobody is surprised either way. */
@Component
public class PaymentWorkerStatusLogger {

    private static final Logger log = LoggerFactory.getLogger(PaymentWorkerStatusLogger.class);

    private final PaymentProperties payment;
    private final CheckoutProperties checkout;

    public PaymentWorkerStatusLogger(PaymentProperties payment, CheckoutProperties checkout) {
        this.payment = payment;
        this.checkout = checkout;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        if (checkout.isOnlineEnabled() && !payment.isMockGatewayEnabled()) {
            log.warn("ONLINE checkout is switched on but the mock payment gateway is OFF: ONLINE stays CLOSED, because "
                    + "an order placed now could never be paid. Enable ONESHOP_PAYMENT_MOCK_GATEWAY_ENABLED too, or "
                    + "switch ONLINE checkout off.");
        }
        if (payment.isTimeoutWorkerEnabled()) {
            log.info("Payment timeout worker is ON (every {} ms): overdue unpaid ONLINE Orders are cancelled and restocked.",
                    payment.getTimeoutScanIntervalMs());
        } else if (checkout.isOnlineEnabled() && payment.isMockGatewayEnabled()) {
            log.warn("Payment timeout worker is OFF (default) while ONLINE checkout is open: unpaid ONLINE Orders keep their "
                    + "stock until an administrator re-checks them in /admin/payments, or until the worker is enabled with "
                    + "ONESHOP_PAYMENT_TIMEOUT_WORKER_ENABLED=true.");
        } else {
            log.info("Payment timeout worker is OFF and ONLINE checkout is closed: no payment data is written in the background.");
        }
    }
}
