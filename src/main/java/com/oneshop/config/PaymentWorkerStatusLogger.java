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
    private final PayosProperties payos;

    public PaymentWorkerStatusLogger(PaymentProperties payment, CheckoutProperties checkout, PayosProperties payos) {
        this.payment = payment;
        this.checkout = checkout;
        this.payos = payos;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        if (payment.getProvider() == com.oneshop.entity.PaymentProvider.PAYOS) {
            String problem = payos.problem();
            if (problem != null) {
                log.error("Payment provider is PAYOS but it is not usable: {}. ONLINE checkout stays CLOSED and no payment "
                        + "link can be created.", problem);
            } else if (checkout.isOnlineEnabled()) {
                log.info("Payment provider is PAYOS (real money). Webhook: {}/api/payments/payos/webhook. Simulation is disabled.",
                        payos.origin());
            } else {
                log.warn("Payment provider is PAYOS and configured, but ONLINE checkout is switched off "
                        + "(ONESHOP_CHECKOUT_ONLINE_ENABLED=false): customers cannot pay online yet.");
            }
        } else if (checkout.isOnlineEnabled() && !payment.isMockGatewayEnabled()) {
            log.warn("ONLINE checkout is switched on but the mock payment gateway is OFF: ONLINE stays CLOSED, because "
                    + "an order placed now could never be paid. Enable ONESHOP_PAYMENT_MOCK_GATEWAY_ENABLED too, or "
                    + "switch ONLINE checkout off.");
        }
        if (payment.isTimeoutWorkerEnabled()) {
            log.info("Payment timeout worker is ON (every {} ms): overdue unpaid ONLINE Orders are cancelled and restocked.",
                    payment.getTimeoutScanIntervalMs());
        } else if (checkout.isOnlineEnabled() && (payment.isMockGatewayEnabled()
                || payment.getProvider() == com.oneshop.entity.PaymentProvider.PAYOS)) {
            log.warn("Payment timeout worker is OFF (default) while ONLINE checkout is open: unpaid ONLINE Orders keep their "
                    + "stock until an administrator re-checks them in /admin/payments, or until the worker is enabled with "
                    + "ONESHOP_PAYMENT_TIMEOUT_WORKER_ENABLED=true.");
        } else {
            log.info("Payment timeout worker is OFF and ONLINE checkout is closed: no payment data is written in the background.");
        }
    }
}
