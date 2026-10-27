package com.oneshop.service.payment;

import com.oneshop.config.PaymentProperties;
import com.oneshop.repository.OrderRepository;
import com.oneshop.service.PaymentDeadlinePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One scan of the payment timeout (BE-05 task B). It only collects candidate Order ids; every decision is taken by
 * {@link PaymentReconciliationService#reconcile} in its own short transaction under the Order lock, so overlapping
 * scans, several application instances and a restart in the middle are all safe: the second actor re-reads the state
 * and has no side effect. No gateway call is made while a database transaction is open (the mock gateway reads the
 * ledger rows the transaction itself holds).
 *
 * <p>Whether a payment is in time is decided from the deadline and the verified collection time, never from the
 * moment this scan happens to run; the scan interval only bounds how late a decision is taken.
 */
@Service
public class PaymentTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(PaymentTimeoutService.class);

    /** @param examined candidates looked at, @param changed Orders whose state changed, @param failed errors */
    public record ScanResult(int examined, int changed, int failed) {
    }

    private final OrderRepository orders;
    private final PaymentReconciliationService reconciliation;
    private final PaymentProperties properties;
    private final PaymentDeadlinePolicy deadlines;

    public PaymentTimeoutService(OrderRepository orders, PaymentReconciliationService reconciliation,
                                 PaymentProperties properties, PaymentDeadlinePolicy deadlines) {
        this.orders = orders;
        this.reconciliation = reconciliation;
        this.properties = properties;
        this.deadlines = deadlines;
    }

    private PaymentEvidenceRefresher refresher;

    /** Present only with a real gateway: its answer is fetched (no lock held) before an Order may be cancelled. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setRefresher(PaymentEvidenceRefresher refresher) {
        this.refresher = refresher;
    }

    public ScanResult scan() {
        LocalDateTime cutoff = deadlines.expiredBefore();
        int batch = properties.getScanBatchSize();
        int examined = 0;
        int changed = 0;
        int failed = 0;
        long afterId = 0;
        while (true) {
            List<Long> ids = orders.findPaymentCandidateIds(cutoff, afterId, PageRequest.of(0, batch));
            if (ids.isEmpty()) {
                break;
            }
            for (Long id : ids) {
                examined++;
                try {
                    if (refresher != null && !refresher.refresh(id)) {
                        failed++; // the provider could not answer: never cancel on stale evidence, retry next scan
                        afterId = id;
                        continue;
                    }
                    PaymentOutcome outcome = reconciliation.reconcile(id, PaymentReconciliationService.Trigger.TIMEOUT_SCAN);
                    if (changes(outcome)) {
                        changed++;
                    }
                } catch (RuntimeException ex) {
                    failed++;
                    log.warn("Payment reconciliation of order {} failed and will be retried by the next scan: {}",
                            id, ex.toString());
                }
                afterId = id;
            }
            if (ids.size() < batch) {
                break;
            }
        }
        return new ScanResult(examined, changed, failed);
    }

    private static boolean changes(PaymentOutcome outcome) {
        return switch (outcome) {
            case CONFIRMED, CANCELLED_PAYMENT_FAILED, CANCELLED_TIMEOUT, CANCELLED_LATE_PAYMENT, LATE_PAYMENT_REFUNDED,
                 NEEDS_ATTENTION -> true;
            default -> false;
        };
    }
}
