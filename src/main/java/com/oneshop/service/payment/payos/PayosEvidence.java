package com.oneshop.service.payment.payos;

import com.oneshop.config.PayosProperties;
import com.oneshop.entity.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;

/**
 * Writes what payOS says onto the {@code payments} row as evidence. It decides nothing about the Order: the
 * reconciliation engine does, through {@link PayosPaymentGateway}. Callers hold the Order write lock.
 *
 * <p>Rules: evidence must name this row (orderCode, and paymentLinkId when both are known) and the same amount; a PAID
 * record is never downgraded by a later, possibly stale, answer; the money is recorded at the bank's own time.
 */
@Component
public class PayosEvidence {

    private static final Logger log = LoggerFactory.getLogger(PayosEvidence.class);

    private final PayosProperties properties;
    private final Clock clock;

    public PayosEvidence(PayosProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Applies the authoritative answer of a link query. @return false when the answer does not belong to the row. */
    public boolean applyLinkInfo(Payment row, PayosApi.LinkInfo info) {
        Long expected = PayosMath.exactVnd(row.getAmount());
        if (info == null || info.orderCode() != row.getProviderOrderCode() || expected == null
                || info.amount() != expected
                || (row.getProviderLinkId() != null && info.id() != null && !row.getProviderLinkId().equals(info.id()))) {
            log.warn("payOS answer does not match payment {} (orderCode/amount/link); ignored", row.getId());
            return false;
        }
        row.setLastSyncedAt(now());
        if (row.getProviderLinkId() == null && info.id() != null) {
            row.setProviderLinkId(info.id());
        }
        String status = info.status();
        if ("PAID".equals(row.getProviderStatus()) && !"PAID".equals(status)) {
            log.warn("payOS reports {} for payment {} that was already recorded as PAID; keeping PAID", status, row.getId());
            return true;
        }
        row.setProviderStatus(status);
        row.setProviderAmountPaid(info.amountPaid());
        if (info.amountPaid() > 0 && info.transactions() != null && !info.transactions().isEmpty()) {
            PayosApi.Transaction latest = info.transactions().stream()
                    .max(Comparator.comparing(t -> t.transactionDateTime() == null ? "" : t.transactionDateTime()))
                    .orElseThrow();
            row.setProviderReference(truncate(latest.reference(), 100));
            // the time that completed the amount: the latest transaction (equal to the single transfer in the usual case)
            row.setProviderPaidAt(PayosMath.bankTime(latest.transactionDateTime(), properties.bankZoneId(), clock.getZone()));
        }
        if ("PAID".equals(row.getProviderStatus()) && row.getProviderPaidAt() == null) {
            // payOS gave no bank time: "paid no later than now" is a safe upper bound (an in-time payment seen after the
            // deadline is then treated as late and refunded, never the reverse)
            row.setProviderPaidAt(now());
        }
        return true;
    }

    /**
     * Applies a verified webhook only when it alone proves a full, successful payment of exactly the row's amount.
     * Anything else (partial amount, odd code, mismatching link) proves nothing by itself and must be settled by a
     * link query. @return true when the webhook was accepted as the evidence of a full payment.
     */
    public boolean applyFullPaymentWebhook(Payment row, PayosApi.WebhookEvent event) {
        Long expected = PayosMath.exactVnd(row.getAmount());
        boolean ok = event.success() && "00".equals(event.code()) && "00".equals(event.dataCode())
                && expected != null && event.amount() != null && event.amount().equals(expected)
                && (event.currency() == null || "VND".equalsIgnoreCase(event.currency()))
                && event.orderCode() != null && event.orderCode().equals(row.getProviderOrderCode())
                && (row.getProviderLinkId() == null || event.paymentLinkId() == null
                || row.getProviderLinkId().equals(event.paymentLinkId()));
        if (!ok) {
            return false;
        }
        if (row.getProviderLinkId() == null && event.paymentLinkId() != null) {
            row.setProviderLinkId(event.paymentLinkId());
        }
        row.setProviderStatus("PAID");
        row.setProviderAmountPaid(event.amount());
        row.setProviderReference(truncate(event.reference(), 100));
        LocalDateTime paidAt = PayosMath.bankTime(event.transactionDateTime(), properties.bankZoneId(), clock.getZone());
        row.setProviderPaidAt(paidAt != null ? paidAt : now()); // same safe upper bound as applyLinkInfo
        row.setLastSyncedAt(now());
        return true;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).withNano(0);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
