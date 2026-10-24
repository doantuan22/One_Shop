package com.oneshop.service.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The {@code @Scheduled} entry of the payment timeout (default every 60 seconds,
 * {@code oneshop.payment.timeout-scan-interval-ms}). No Redis and no queue: a single database-backed scan. Switched
 * off with {@code oneshop.payment.timeout-worker-enabled=false} (the test profile does, and calls the scan directly).
 */
@Component
@ConditionalOnProperty(name = "oneshop.payment.timeout-worker-enabled", havingValue = "true")
public class PaymentTimeoutWorker {

    private static final Logger log = LoggerFactory.getLogger(PaymentTimeoutWorker.class);

    private final PaymentTimeoutService service;

    public PaymentTimeoutWorker(PaymentTimeoutService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${oneshop.payment.timeout-scan-interval-ms:60000}",
            initialDelayString = "${oneshop.payment.timeout-scan-interval-ms:60000}")
    public void run() {
        try {
            PaymentTimeoutService.ScanResult result = service.scan();
            if (result.changed() > 0 || result.failed() > 0) {
                log.info("Payment timeout scan: examined={}, changed={}, failed={}", result.examined(),
                        result.changed(), result.failed());
            }
        } catch (RuntimeException ex) {
            log.error("Payment timeout scan failed; the next scan will retry", ex);
        }
    }
}
