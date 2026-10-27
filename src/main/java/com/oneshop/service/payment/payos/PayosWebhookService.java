package com.oneshop.service.payment.payos;

import com.oneshop.config.PayosProperties;
import com.oneshop.entity.Order;
import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentProvider;
import com.oneshop.entity.PaymentProviderEvent;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.PaymentProviderEventRepository;
import com.oneshop.repository.PaymentRepository;
import com.oneshop.service.payment.PaymentReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;

/**
 * Handles a payOS webhook. Order of work:
 * <ol>
 *   <li>the signature is verified (official SDK, checksum key) BEFORE anything is read from the body;</li>
 *   <li>one transaction under the Order lock: the event key is looked up (a repeat is a no-op), the event is
 *       matched to a stored payment by orderCode, paymentLinkId, amount and currency, evidence is stored and the
 *       reconciliation engine decides; the journal row and the evidence commit together or not at all;</li>
 *   <li>a webhook that does not by itself prove a FULL payment (partial amount, unexpected code) only triggers a
 *       link query, which is the authoritative source.</li>
 * </ol>
 * An unknown orderCode (for example payOS's own sample sent when the URL is confirmed) is acknowledged and changes
 * nothing. Every verified delivery is acknowledged with 2xx unless processing itself failed, so payOS retries those.
 */
@Service
@ConditionalOnProperty(name = "oneshop.payment.provider", havingValue = "PAYOS")
public class PayosWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PayosWebhookService.class);

    public enum Result { ACCEPTED, DUPLICATE, INVALID_SIGNATURE }

    private record Processed(boolean duplicate, Long orderId, boolean needsSync) {
    }

    private final PayosApi api;
    private final PayosEvidence evidence;
    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final PaymentProviderEventRepository events;
    private final PaymentReconciliationService reconciliation;
    private final PayosPaymentFlow flow;
    private final Clock clock;
    private final PayosProperties properties;
    private final TransactionTemplate tx;

    public PayosWebhookService(PayosApi api, PayosEvidence evidence, PaymentRepository payments, OrderRepository orders,
                               PaymentProviderEventRepository events, PaymentReconciliationService reconciliation,
                               PayosPaymentFlow flow, Clock clock, PayosProperties properties,
                               PlatformTransactionManager transactionManager) {
        this.api = api;
        this.evidence = evidence;
        this.payments = payments;
        this.orders = orders;
        this.events = events;
        this.reconciliation = reconciliation;
        this.flow = flow;
        this.clock = clock;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setTimeout(15);
    }

    public Result handle(String rawBody) {
        PayosApi.WebhookEvent event;
        try {
            event = api.verifyWebhook(rawBody);
        } catch (PayosException.InvalidSignature ex) {
            log.warn("payOS webhook with an invalid signature was rejected");
            return Result.INVALID_SIGNATURE;
        }
        String key = eventKey(event);
        Processed processed;
        try {
            processed = tx.execute(status -> process(event, key));
        } catch (DataIntegrityViolationException duplicate) {
            // two deliveries raced; the other one committed the same event
            return Result.DUPLICATE;
        }
        if (processed.duplicate()) {
            return Result.DUPLICATE;
        }
        if (processed.needsSync() && processed.orderId() != null) {
            try {
                flow.sync(processed.orderId(), PaymentReconciliationService.Trigger.PROVIDER_WEBHOOK);
            } catch (RuntimeException ex) {
                // the event is journaled; the return page, the timeout scan or an Admin recheck will query payOS again
                log.warn("Follow-up payOS query for order {} failed: {}", processed.orderId(), ex.toString());
            }
        }
        return Result.ACCEPTED;
    }

    private Processed process(PayosApi.WebhookEvent event, String key) {
        if (events.findByProviderAndEventKey(PaymentProvider.PAYOS, key).isPresent()) {
            return new Processed(true, null, false);
        }
        PaymentProviderEvent journal = new PaymentProviderEvent();
        journal.setProvider(PaymentProvider.PAYOS);
        journal.setEventKey(key);
        journal.setOrderCode(event.orderCode());
        journal.setPaymentLinkId(event.paymentLinkId());
        journal.setReference(truncate(event.reference(), 100));
        journal.setAmount(event.amount());
        journal.setEventCode(truncate(event.dataCode(), 10));
        journal.setTransactionAt(PayosMath.bankTime(event.transactionDateTime(), properties.bankZoneId(),
                clock.getZone()));

        Long orderId = event.orderCode() == null ? null : payments.findOrderIdByProviderOrderCode(event.orderCode())
                .orElse(null);
        if (orderId == null) {
            journal.setOutcome(PaymentProviderEvent.Outcome.UNKNOWN_ORDER); // e.g. payOS's own sample on URL confirm
            events.saveAndFlush(journal);
            return new Processed(false, null, false);
        }
        Order order = orders.findByIdForUpdate(orderId).orElseThrow();
        Payment row = payments.findByProviderOrderCode(event.orderCode()).orElseThrow(); // read after the lock
        journal.setPaymentId(row.getId());
        boolean linkMismatch = row.getProviderLinkId() != null && event.paymentLinkId() != null
                && !row.getProviderLinkId().equals(event.paymentLinkId());
        if (linkMismatch) {
            journal.setOutcome(PaymentProviderEvent.Outcome.REJECTED_MISMATCH);
            events.saveAndFlush(journal);
            return new Processed(false, orderId, false);
        }
        boolean full = evidence.applyFullPaymentWebhook(row, event);
        if (full) {
            payments.saveAndFlush(row);
            journal.setOutcome(PaymentProviderEvent.Outcome.APPLIED);
            events.saveAndFlush(journal);
            reconciliation.reconcileLocked(order, PaymentReconciliationService.Trigger.PROVIDER_WEBHOOK, null);
            return new Processed(false, orderId, false);
        }
        journal.setOutcome(PaymentProviderEvent.Outcome.TRIGGERED_SYNC);
        events.saveAndFlush(journal);
        return new Processed(false, orderId, true);
    }

    /** Stable across redeliveries of the same transaction; differs between different transactions. */
    static String eventKey(PayosApi.WebhookEvent event) {
        String text = String.join("|", String.valueOf(event.orderCode()), String.valueOf(event.paymentLinkId()),
                String.valueOf(event.reference()), String.valueOf(event.amount()), String.valueOf(event.dataCode()),
                String.valueOf(event.transactionDateTime()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
