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
    /** Interval of the timeout scan (BE-05). */
    private long timeoutScanIntervalMs = 60000;
    /** Runs the {@code @Scheduled} timeout worker. Off in tests (they call the scan directly) and may be off per node. */
    private boolean timeoutWorkerEnabled = true;
    /** At most this many Orders are examined per scan; the next scan continues. */
    private int scanBatchSize = 200;
    /**
     * The server-side mock payment gateway (DEC-21): demo/test/dev only. When false the customer simulation and the
     * Admin gateway console are refused, so nothing in production can settle a payment by a button.
     */
    private boolean mockGatewayEnabled = true;

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

    public boolean isTimeoutWorkerEnabled() {
        return timeoutWorkerEnabled;
    }

    public void setTimeoutWorkerEnabled(boolean timeoutWorkerEnabled) {
        this.timeoutWorkerEnabled = timeoutWorkerEnabled;
    }

    public int getScanBatchSize() {
        return scanBatchSize;
    }

    public void setScanBatchSize(int scanBatchSize) {
        if (scanBatchSize < 1) {
            throw new IllegalArgumentException("oneshop.payment.scan-batch-size must be >= 1");
        }
        this.scanBatchSize = scanBatchSize;
    }

    public boolean isMockGatewayEnabled() {
        return mockGatewayEnabled;
    }

    public void setMockGatewayEnabled(boolean mockGatewayEnabled) {
        this.mockGatewayEnabled = mockGatewayEnabled;
    }
}
