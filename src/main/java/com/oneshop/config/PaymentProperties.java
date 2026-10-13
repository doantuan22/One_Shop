package com.oneshop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ONLINE payment timing (DEC-15, DEC-17, DEC-20). The business values are 15 + 15 minutes; they are not to be
 * changed through environment variables without the project owner's approval. The scan interval is operational.
 */
@ConfigurationProperties(prefix = "oneshop.payment")
public class PaymentProperties {

    /** Minutes a customer has to pay, counted from {@code orders.created_at}. */
    private int pendingTimeoutMinutes = 15;
    /** Minutes after the payment deadline during which an UNKNOWN result is reconciled (used from BE-05). */
    private int reconciliationWindowMinutes = 15;
    /** Interval of the timeout scan (used from BE-05). */
    private long timeoutScanIntervalMs = 60000;

    public int getPendingTimeoutMinutes() {
        return pendingTimeoutMinutes;
    }

    public void setPendingTimeoutMinutes(int pendingTimeoutMinutes) {
        if (pendingTimeoutMinutes < 1) {
            throw new IllegalArgumentException("oneshop.payment.pending-timeout-minutes must be >= 1");
        }
        this.pendingTimeoutMinutes = pendingTimeoutMinutes;
    }

    public int getReconciliationWindowMinutes() {
        return reconciliationWindowMinutes;
    }

    public void setReconciliationWindowMinutes(int reconciliationWindowMinutes) {
        if (reconciliationWindowMinutes < 1) {
            throw new IllegalArgumentException("oneshop.payment.reconciliation-window-minutes must be >= 1");
        }
        this.reconciliationWindowMinutes = reconciliationWindowMinutes;
    }

    public long getTimeoutScanIntervalMs() {
        return timeoutScanIntervalMs;
    }

    public void setTimeoutScanIntervalMs(long timeoutScanIntervalMs) {
        if (timeoutScanIntervalMs < 1000) {
            throw new IllegalArgumentException("oneshop.payment.timeout-scan-interval-ms must be >= 1000");
        }
        this.timeoutScanIntervalMs = timeoutScanIntervalMs;
    }
}
