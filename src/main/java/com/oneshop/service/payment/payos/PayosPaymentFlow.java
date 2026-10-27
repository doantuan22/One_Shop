package com.oneshop.service.payment.payos;

import com.oneshop.config.PayosProperties;
import com.oneshop.entity.Order;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.PaymentProvider;
import com.oneshop.entity.PaymentStatus;
import com.oneshop.exception.PaymentConflictException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.PaymentRepository;
import com.oneshop.service.PaymentDeadlinePolicy;
import com.oneshop.service.payment.GatewayVerification;
import com.oneshop.service.payment.PaymentEvidenceRefresher;
import com.oneshop.service.payment.PaymentGateway;
import com.oneshop.service.payment.PaymentOutcome;
import com.oneshop.service.payment.PaymentReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hosted-checkout flow with payOS. Every payOS HTTP call happens OUTSIDE a database transaction: a short transaction
 * (Order write lock) reads or writes the Payment row, the call is made with no lock held, and a second short
 * transaction re-checks the state under the lock before storing the result.
 *
 * <p>Crash safety: the unique {@code orderCode} is stored on the Payment row <em>before</em> payOS is called. If the
 * link was created but the answer was lost (or the process died before saving it), the next attempt re-sends the SAME
 * orderCode; payOS refuses the duplicate, and the existing link is then looked up by that orderCode and adopted. A
 * second payment request is never created for one attempt.
 *
 * <p>Idempotency: while an attempt is pending and has a checkout URL, a repeated click returns that same URL and does
 * not touch payOS; the link expires at the Order's payment deadline, never later.
 */
@Service
@ConditionalOnProperty(name = "oneshop.payment.provider", havingValue = "PAYOS")
public class PayosPaymentFlow implements PaymentEvidenceRefresher {

    private static final Logger log = LoggerFactory.getLogger(PayosPaymentFlow.class);
    /** A link must live at least this long to be worth creating. */
    private static final long MIN_LINK_SECONDS = 30;
    private static final int TX_TIMEOUT_SECONDS = 15;

    private final PayosApi api;
    private final PayosProperties properties;
    private final PayosEvidence evidence;
    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final PaymentDeadlinePolicy deadlines;
    private final PaymentGateway gateway;
    private final PaymentReconciliationService reconciliation;
    private final Clock clock;
    private final PayosLinkCanceller canceller;
    private final TransactionTemplate tx;

    public PayosPaymentFlow(PayosApi api, PayosProperties properties, PayosEvidence evidence,
                            PaymentRepository payments, OrderRepository orders, PaymentDeadlinePolicy deadlines,
                            PaymentGateway gateway, PaymentReconciliationService reconciliation, Clock clock,
                            PayosLinkCanceller canceller, PlatformTransactionManager transactionManager) {
        this.api = api;
        this.properties = properties;
        this.evidence = evidence;
        this.payments = payments;
        this.orders = orders;
        this.deadlines = deadlines;
        this.gateway = gateway;
        this.reconciliation = reconciliation;
        this.clock = clock;
        this.canceller = canceller;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setTimeout(TX_TIMEOUT_SECONDS);
    }

    /** Where to send the customer; {@code checkoutUrl} is null when there is nothing to pay any more. */
    public record StartResult(String checkoutUrl) {
    }

    private record Prepared(Long paymentId, long orderCode, long amount, LocalDateTime expiresAt, String existingUrl,
                            boolean settled) {
    }

    // ------------------------------------------------------------------ start

    public StartResult start(String customerEmail, Long orderId) {
        Prepared prepared = tx.execute(status -> prepare(customerEmail, orderId));
        if (prepared.settled()) {
            return new StartResult(null);
        }
        if (prepared.existingUrl() != null) {
            return new StartResult(prepared.existingUrl());
        }
        PayosApi.LinkRequest request = new PayosApi.LinkRequest(prepared.orderCode(), prepared.amount(),
                PayosMath.description(orderId), "Don hang OneShop #" + orderId,
                properties.origin() + "/orders/" + orderId + "/payments/payos/return",
                properties.origin() + "/orders/" + orderId + "/payments/payos/cancel",
                prepared.expiresAt().atZone(clock.getZone()).toEpochSecond());

        PayosApi.LinkCreated created = null;
        PayosApi.LinkInfo adopted = null;
        try {
            created = api.createLink(request);
        } catch (PayosException.NotConfigured ex) {
            throw new PaymentConflictException(PaymentConflictException.GATEWAY_DISABLED,
                    "Cổng thanh toán payOS chưa được cấu hình.");
        } catch (PayosException ex) {
            // refused as a duplicate, or the answer was lost: the link may already exist under this orderCode
            log.warn("payOS createLink for orderCode {} failed ({}); looking the link up", prepared.orderCode(),
                    ex.getClass().getSimpleName());
            try {
                adopted = api.getLink(prepared.orderCode());
            } catch (PayosException.NotFound notFound) {
                if (ex instanceof PayosException.Rejected) {
                    discardAttempt(prepared.paymentId());
                    throw new PaymentConflictException(PaymentConflictException.GATEWAY_UNAVAILABLE,
                            "payOS từ chối tạo link thanh toán. Vui lòng thử lại hoặc chọn phương thức khác.");
                }
                throw unavailable();
            } catch (PayosException lookup) {
                throw unavailable();
            }
        }
        final PayosApi.LinkCreated link = created;
        final PayosApi.LinkInfo existing = adopted;
        String url = tx.execute(status -> finish(prepared, link, existing));
        if (url == null) {
            canceller.cancelQuietly(prepared.orderCode()); // the Order stopped being payable while the link was being made
        }
        return new StartResult(url);
    }

    private Prepared prepare(String customerEmail, Long orderId) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(PayosPaymentFlow::orderNotFound);
        if (!order.getUser().getEmail().equals(customerEmail)) {
            throw orderNotFound();
        }
        if (order.getPaymentMethod() != PaymentMethod.ONLINE) {
            throw new PaymentConflictException(PaymentConflictException.NOT_A_PAYMENT_CASE,
                    "Đơn hàng này không sử dụng thanh toán trực tuyến.");
        }
        if (!gateway.onlineAvailable()) {
            throw new PaymentConflictException(PaymentConflictException.GATEWAY_DISABLED,
                    "Thanh toán trực tuyến hiện chưa khả dụng.");
        }
        List<Payment> rows = payments.findForLockedOrder(orderId);
        GatewayVerification verification = gateway.verify(order, rows);
        if (verification.result() == GatewayVerification.Result.SUCCESS) {
            // the bank already has the money: let the engine apply it, never open a second link
            reconciliation.reconcileLocked(order, PaymentReconciliationService.Trigger.CUSTOMER_CALLBACK, null);
            return new Prepared(null, 0, 0, null, null, true);
        }
        if (verification.result() == GatewayVerification.Result.UNKNOWN) {
            throw new PaymentConflictException(PaymentConflictException.PAYMENT_RECONCILING,
                    "Giao dịch của đơn hàng đang được đối soát; chưa thể tạo thanh toán mới.");
        }
        if (order.getOrderStatus() != OrderStatus.PENDING_PAYMENT || order.getPaymentStatus() != OrderPaymentStatus.UNPAID
                || !deadlines.isPayableNow(order)) {
            throw new PaymentConflictException(PaymentConflictException.PAYMENT_NOT_PAYABLE,
                    "Đơn hàng không còn chờ thanh toán trực tuyến.");
        }
        LocalDateTime deadline = deadlines.paymentDeadline(order);
        if (!deadlines.now().plusSeconds(MIN_LINK_SECONDS).isBefore(deadline)) {
            throw new PaymentConflictException(PaymentConflictException.PAYMENT_NOT_PAYABLE,
                    "Đơn hàng sắp hết hạn thanh toán; không thể tạo thêm link thanh toán.");
        }
        long amount = PayosMath.toVnd(order.getTotalAmount(), properties);

        Payment current = rows.stream().filter(p -> p.isPayos() && p.getStatus() == PaymentStatus.PENDING)
                .findFirst().orElse(null);
        if (current != null && closedAtProvider(current)) {
            current.setStatus(PaymentStatus.FAILED); // payOS closed that link; only one pending link per Order exists
            payments.saveAndFlush(current);
            current = null;
        }
        if (current != null && current.getCheckoutUrl() != null) {
            return new Prepared(current.getId(), current.getProviderOrderCode(), amount, deadline,
                    current.getCheckoutUrl(), false);
        }
        if (current == null) {
            current = newAttempt(order, deadline);
        }
        return new Prepared(current.getId(), current.getProviderOrderCode(), amount, deadline, null, false);
    }

    private Payment newAttempt(Order order, LocalDateTime deadline) {
        long code = PayosMath.newOrderCode(deadlines.now(), clock.getZone());
        for (int tries = 0; payments.existsByProviderOrderCode(code); tries++) {
            if (tries >= 5) {
                throw new IllegalStateException("Không tạo được mã đơn payOS duy nhất.");
            }
            code = PayosMath.newOrderCode(deadlines.now(), clock.getZone());
        }
        Payment attempt = new Payment();
        attempt.setOrder(order);
        attempt.setMethod(PaymentMethod.ONLINE);
        attempt.setAmount(order.getTotalAmount()); // the whole Order, read from the database
        attempt.setStatus(PaymentStatus.PENDING);
        attempt.setProvider(PaymentProvider.PAYOS);
        attempt.setProviderOrderCode(code);
        attempt.setTransactionCode("PAYOS-" + code);
        attempt.setExpiresAt(deadline);
        return payments.saveAndFlush(attempt);
    }

    private String finish(Prepared prepared, PayosApi.LinkCreated created, PayosApi.LinkInfo existing) {
        Long orderId = payments.findOrderIdById(prepared.paymentId()).orElseThrow();
        Order order = orders.findByIdForUpdate(orderId).orElseThrow();
        Payment row = payments.findById(prepared.paymentId()).orElseThrow(); // read only after the lock
        if (row.getStatus() != PaymentStatus.PENDING || order.getOrderStatus() != OrderStatus.PENDING_PAYMENT) {
            return null;
        }
        String linkId = created != null ? created.paymentLinkId() : existing.id();
        row.setProviderLinkId(linkId);
        row.setExpiresAt(prepared.expiresAt());
        row.setLastSyncedAt(LocalDateTime.now(clock).withNano(0));
        if (created != null) {
            row.setCheckoutUrl(created.checkoutUrl());
            row.setProviderStatus(created.status() == null ? "PENDING" : created.status());
            payments.saveAndFlush(row);
            return row.getCheckoutUrl();
        }
        // adopted an existing link: take payOS's own state as evidence and let the engine decide if it is already paid
        evidence.applyLinkInfo(row, existing);
        row.setCheckoutUrl(properties.getCheckoutBaseUrl() + linkId);
        payments.saveAndFlush(row);
        reconciliation.reconcileLocked(order, PaymentReconciliationService.Trigger.CUSTOMER_CALLBACK, null);
        return "PENDING".equals(row.getProviderStatus()) && row.getStatus() == PaymentStatus.PENDING
                ? row.getCheckoutUrl() : null;
    }

    /** payOS refused the link definitively: close the empty attempt so a retry starts clean. */
    private void discardAttempt(Long paymentId) {
        tx.executeWithoutResult(status -> payments.findOrderIdById(paymentId).ifPresent(orderId -> {
            orders.findByIdForUpdate(orderId);
            payments.findById(paymentId).ifPresent(row -> {
                if (row.getStatus() == PaymentStatus.PENDING && row.getProviderLinkId() == null) {
                    row.setStatus(PaymentStatus.FAILED);
                    payments.saveAndFlush(row);
                }
            });
        }));
    }

    // ------------------------------------------------------------------ reconciliation against payOS

    /**
     * Asks payOS about every attempt of the Order that could still hold money and stores the answers as evidence.
     * Returns false when payOS could not answer for any of them (then nothing may be decided from stale evidence).
     */
    @Override
    public boolean refresh(Long orderId) {
        List<long[]> targets = tx.execute(status -> {
            List<Payment> rows = payments.findByOrderIdOrderByIdAsc(orderId).stream()
                    .filter(p -> p.isPayos() && p.getProviderOrderCode() != null && p.getStatus() != PaymentStatus.SUCCESS)
                    .toList();
            int skip = Math.max(0, rows.size() - 5); // an Order never has more than a few attempts; bound the calls
            return rows.stream().skip(skip).map(p -> new long[]{p.getId(), p.getProviderOrderCode()}).toList();
        });
        if (targets == null || targets.isEmpty()) {
            return true;
        }
        Map<Long, PayosApi.LinkInfo> answers = new LinkedHashMap<>();
        boolean complete = true;
        for (long[] target : targets) {
            try {
                answers.put(target[0], api.getLink(target[1]));
            } catch (PayosException.NotFound ex) {
                // never created at payOS (the process stopped before the call): nothing there to learn
            } catch (PayosException ex) {
                complete = false;
                log.warn("payOS lookup of orderCode {} failed: {}", target[1], ex.getMessage());
            }
        }
        if (!answers.isEmpty()) {
            tx.executeWithoutResult(status -> {
                orders.findByIdForUpdate(orderId);
                for (Map.Entry<Long, PayosApi.LinkInfo> answer : answers.entrySet()) {
                    payments.findById(answer.getKey()).filter(p -> p.getOrder().getId().equals(orderId))
                            .ifPresent(row -> {
                                if (row.getStatus() != PaymentStatus.SUCCESS) {
                                    evidence.applyLinkInfo(row, answer.getValue());
                                    payments.saveAndFlush(row);
                                }
                            });
                }
            });
        }
        return complete;
    }

    /** Refresh from payOS, then let the engine decide. If payOS cannot answer, nothing is decided. */
    public PaymentOutcome sync(Long orderId, PaymentReconciliationService.Trigger trigger) {
        if (!refresh(orderId)) {
            return PaymentOutcome.RECONCILING;
        }
        return reconciliation.reconcile(orderId, trigger);
    }

    // ------------------------------------------------------------------ helpers

    private static boolean closedAtProvider(Payment row) {
        String status = row.getProviderStatus();
        return "CANCELLED".equals(status) || "EXPIRED".equals(status) || "FAILED".equals(status);
    }

    private static PaymentConflictException unavailable() {
        return new PaymentConflictException(PaymentConflictException.GATEWAY_UNAVAILABLE,
                "Chưa kết nối được payOS. Đơn hàng chưa bị trừ tiền; vui lòng thử lại sau ít phút.");
    }

    private static ResourceNotFoundException orderNotFound() {
        return new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn.");
    }
}
