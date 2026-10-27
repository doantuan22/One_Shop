package com.oneshop;

import com.oneshop.service.CartService;
import com.oneshop.service.CheckoutService;
import com.oneshop.service.CustomerOrderService;
import com.oneshop.service.payment.ManualRefundService;
import com.oneshop.service.payment.PaymentOutcome;
import com.oneshop.service.payment.PaymentReconciliationService;
import com.oneshop.service.payment.RefundSimulator;
import com.oneshop.service.payment.payos.PayosApi;
import com.oneshop.service.payment.payos.PayosPaymentFlow;
import com.oneshop.service.payment.payos.PayosWebhookService;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.StoreRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static com.oneshop.FulfillmentFixtures.ALICE;
import static com.oneshop.FulfillmentFixtures.SERUM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * payOS on the real SQL Server (a {@code oneshop*_test} database only): real checkout, real locks, real constraints;
 * only the payOS HTTP API is replaced by an in-memory fake that behaves like payOS (links, payments, errors).
 *
 * <p>These tests commit; {@link SeedGuardV3} puts the test database back.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=test-only-secret-test-only-secret-1234567890",
        "oneshop.checkout.online-enabled=true",
        "oneshop.payment.provider=PAYOS",
        "oneshop.payos.client-id=test-client",
        "oneshop.payos.api-key=test-key",
        "oneshop.payos.checksum-key=test-checksum",
        "oneshop.payos.public-base-url=https://shop.example.com"})
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class PayosDatabaseIntegrationTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter BANK = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String ADMIN = "admin@example.com";

    @MockitoBean PayosApi api;
    @MockitoSpyBean RefundSimulator simulator;
    @Autowired PayosPaymentFlow flow;
    @Autowired PayosWebhookService webhooks;
    @Autowired PaymentReconciliationService reconciliation;
    @Autowired ManualRefundService manualRefunds;
    @Autowired CustomerOrderService customerOrders;
    @Autowired CartService cartService;
    @Autowired CheckoutService checkoutService;
    @Autowired CartRepository cartRepository;
    @Autowired CartItemRepository cartItemRepository;
    @Autowired ProductRepository productRepository;
    @Autowired StoreRepository storeRepository;
    @Autowired StoreProductRepository storeProductRepository;
    @Autowired JdbcTemplate jdbc;

    private SeedGuardV3 guard;
    private FulfillmentFixtures data;
    private boolean remembered;
    private Fake payos;

    @BeforeEach
    void setUp() {
        guard = new SeedGuardV3(jdbc);
        assumeTrue(guard.isTestDatabase(), "these tests commit: run against a oneshop*_test database");
        guard.remember();
        remembered = true;
        data = new FulfillmentFixtures(jdbc, cartService, checkoutService, cartRepository, cartItemRepository,
                productRepository, storeRepository, storeProductRepository);
        reset(api);
        payos = new Fake();
        when(api.createLink(any())).thenAnswer(i -> payos.create(i.getArgument(0)));
        when(api.getLink(anyLong())).thenAnswer(i -> payos.get(i.getArgument(0)));
        when(api.cancelLink(anyLong(), any())).thenAnswer(i -> payos.cancel(i.getArgument(0)));
    }

    @AfterEach
    void restore() {
        if (remembered) {
            guard.restore();
        }
    }

    /** A payOS that remembers links, can be paid at a chosen BANK time, and can be switched off. */
    final class Fake {
        final Map<Long, PayosApi.LinkRequest> requests = new java.util.concurrent.ConcurrentHashMap<>();
        final Map<Long, String> status = new java.util.concurrent.ConcurrentHashMap<>();
        final Map<Long, Long> paid = new java.util.concurrent.ConcurrentHashMap<>();
        final Map<Long, String> bankTime = new java.util.concurrent.ConcurrentHashMap<>();
        final AtomicInteger creates = new AtomicInteger();
        volatile boolean down;

        PayosApi.LinkCreated create(PayosApi.LinkRequest request) {
            if (down) {
                throw new com.oneshop.service.payment.payos.PayosException.Unavailable("down", null);
            }
            if (requests.containsKey(request.orderCode())) {
                throw new com.oneshop.service.payment.payos.PayosException.Rejected("order code exists", null);
            }
            creates.incrementAndGet();
            requests.put(request.orderCode(), request);
            status.put(request.orderCode(), "PENDING");
            return new PayosApi.LinkCreated("link-" + request.orderCode(), "https://pay.payos.vn/web/link-" + request.orderCode(),
                    request.orderCode(), request.amount(), "PENDING");
        }

        PayosApi.LinkInfo get(long code) {
            if (down) {
                throw new com.oneshop.service.payment.payos.PayosException.Unavailable("down", null);
            }
            PayosApi.LinkRequest request = requests.get(code);
            if (request == null) {
                throw new com.oneshop.service.payment.payos.PayosException.NotFound("none", null);
            }
            long amountPaid = paid.getOrDefault(code, 0L);
            List<PayosApi.Transaction> tx = amountPaid > 0
                    ? List.of(new PayosApi.Transaction("FT" + code, amountPaid, bankTime.get(code))) : List.of();
            return new PayosApi.LinkInfo("link-" + code, code, request.amount(), amountPaid, request.amount() - amountPaid,
                    status.get(code), tx);
        }

        PayosApi.LinkInfo cancel(long code) {
            if (paid.getOrDefault(code, 0L) > 0) {
                throw new com.oneshop.service.payment.payos.PayosException.Rejected("already paid", null);
            }
            status.put(code, "CANCELLED");
            return get(code);
        }

        /** The customer's bank transfer reaches payOS at {@code serverTime} (stored in the server zone). */
        void pay(long code, long amount, LocalDateTime serverTime) {
            long total = paid.merge(code, amount, Long::sum);
            bankTime.put(code, serverTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(VN).format(BANK));
            status.put(code, total == requests.get(code).amount() ? "PAID" : total > requests.get(code).amount() ? "PAID" : "UNDERPAID");
        }
    }

    // ------------------------------------------------------------------ helpers

    private long order() {
        return data.onlinePickup("OS-THUDUC", SERUM, 2);
    }

    private Map<String, Object> row(long orderId) {
        return jdbc.queryForList("select * from dbo.payments where order_id = ? and provider = 'PAYOS' order by payment_id desc", orderId).get(0);
    }

    private long code(long orderId) {
        return ((Number) row(orderId).get("provider_order_code")).longValue();
    }

    private long total(long orderId) {
        return jdbc.queryForObject("select total_amount from dbo.orders where order_id = ?", java.math.BigDecimal.class, orderId).longValueExact();
    }

    private LocalDateTime created(long orderId) {
        return jdbc.queryForObject("select created_at from dbo.orders where order_id = ?", LocalDateTime.class, orderId);
    }

    private String start(long orderId) {
        return flow.start(ALICE, orderId).checkoutUrl();
    }

    private PayosApi.WebhookEvent webhookFor(long orderId, long amount, String ref, LocalDateTime serverTime) {
        return new PayosApi.WebhookEvent(true, "00", code(orderId), amount, "VND", "link-" + code(orderId), ref,
                serverTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(VN).format(BANK), "00");
    }

    private PayosWebhookService.Result deliver(String raw, PayosApi.WebhookEvent event) {
        when(api.verifyWebhook(raw)).thenReturn(event);
        return webhooks.handle(raw);
    }

    private int events(long orderId) {
        return jdbc.queryForObject("select count(*) from dbo.payment_provider_events e join dbo.payments p on p.payment_id = e.payment_id "
                + "where p.order_id = ?", Integer.class, orderId);
    }

    private int ledger(long orderId) {
        return jdbc.queryForObject("select count(*) from dbo.payment_refunds where order_id = ?", Integer.class, orderId);
    }

    private <T> T asAdmin(java.util.function.Supplier<T> action) {
        return data.asRole(ADMIN, "ROLE_ADMIN", action);
    }

    // ================================================================== link creation and reuse

    @Test
    @DisplayName("PAYOS-01 a link is created from the database total, stored with its unique orderCode, expiring at the order deadline")
    void linkIsCreatedAndStored() {
        long order = order();
        assertThat(jdbc.queryForObject("select count(*) from dbo.payments where order_id = ?", Integer.class, order))
                .isZero(); // checkout made no (mock) attempt: the link is made when the customer presses pay

        String url = start(order);

        Map<String, Object> row = row(order);
        assertThat(url).startsWith("https://pay.payos.vn/web/");
        assertThat(row.get("provider")).isEqualTo("PAYOS");
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("checkout_url")).isEqualTo(url);
        assertThat(row.get("provider_link_id")).isEqualTo("link-" + code(order));
        assertThat(payos.requests.get(code(order)).amount()).isEqualTo(total(order));
        assertThat(((java.sql.Timestamp) row.get("expires_at")).toLocalDateTime())
                .isEqualTo(created(order).plusMinutes(15));
        assertThat(payos.requests.get(code(order)).returnUrl())
                .isEqualTo("https://shop.example.com/orders/" + order + "/payments/payos/return");
    }

    @Test
    @DisplayName("PAYOS-02 repeated and concurrent clicks create exactly one link")
    void repeatedAndConcurrentClicksReuseOneLink() throws Exception {
        long order = order();
        String first = start(order);
        assertThat(start(order)).isEqualTo(first);

        long second = order();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                results.add(pool.submit((Callable<String>) () -> start(second)));
            }
            for (Future<String> result : results) {
                assertThat(result.get()).isEqualTo(results.get(0).get());
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from dbo.payments where order_id = ?", Integer.class, second)).isEqualTo(1);
        assertThat(payos.creates.get()).isEqualTo(2); // one link per order, not one per click
    }

    @Test
    @DisplayName("PAYOS-03 a crash after payOS created the link is recovered by the stored orderCode, not by a second request")
    void crashBeforeSavingTheLinkIsRecovered() {
        long order = order();
        payos.down = true;
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> start(order))
                .isInstanceOf(com.oneshop.exception.PaymentConflictException.class);
        long stored = code(order);
        assertThat(row(order).get("provider_link_id")).isNull();

        // payOS had in fact created the link before the connection dropped
        payos.down = false;
        payos.requests.put(stored, new PayosApi.LinkRequest(stored, total(order), "OneShop", "x", "r", "c", 0));
        payos.status.put(stored, "PENDING");

        String url = start(order);

        assertThat(url).isEqualTo("https://pay.payos.vn/web/link-" + stored);
        assertThat(jdbc.queryForObject("select count(*) from dbo.payments where order_id = ?", Integer.class, order)).isEqualTo(1);
        assertThat(code(order)).isEqualTo(stored);
    }

    // ================================================================== payment

    @Test
    @DisplayName("PAYOS-04 a signed full-payment webhook confirms the order once; duplicates and replays change nothing")
    void webhookConfirmsOnceAndDuplicatesAreHarmless() {
        long order = order();
        start(order);
        LocalDateTime paidAt = created(order).plusMinutes(3);
        payos.pay(code(order), total(order), paidAt);
        int stock = data.quantityAt(SERUM, "OS-THUDUC");

        assertThat(deliver("raw-1", webhookFor(order, total(order), "FT-1", paidAt))).isEqualTo(PayosWebhookService.Result.ACCEPTED);
        assertThat(deliver("raw-1", webhookFor(order, total(order), "FT-1", paidAt))).isEqualTo(PayosWebhookService.Result.DUPLICATE);
        assertThat(deliver("raw-1", webhookFor(order, total(order), "FT-1", paidAt))).isEqualTo(PayosWebhookService.Result.DUPLICATE);

        assertThat(data.orderStatus(order)).isEqualTo("CONFIRMED");
        assertThat(data.paymentStatus(order)).isEqualTo("PAID");
        Map<String, Object> row = row(order);
        assertThat(row.get("status")).isEqualTo("SUCCESS");
        assertThat(((java.sql.Timestamp) row.get("paid_at")).toLocalDateTime()).isEqualTo(paidAt.withNano(0)); // the bank's time
        assertThat(row.get("provider_status")).isEqualTo("PAID");
        assertThat(events(order)).isEqualTo(1);
        assertThat(data.quantityAt(SERUM, "OS-THUDUC")).isEqualTo(stock); // confirmation never touches stock
        assertThat(ledger(order)).isZero();
    }

    @Test
    @DisplayName("PAYOS-05 a partial amount, an unknown orderCode (payOS's sample) and a missed webhook")
    void partialUnknownAndMissedWebhooks() {
        long order = order();
        start(order);
        LocalDateTime paidAt = created(order).plusMinutes(2);

        // payOS's own sample on URL confirmation: unknown orderCode, signed but meaningless to us
        assertThat(deliver("sample", new PayosApi.WebhookEvent(true, "00", 123L, 3000L, "VND", "124c3329", "TF" + System.nanoTime(),
                "2023-02-04 18:25:00", "00"))).isEqualTo(PayosWebhookService.Result.ACCEPTED);
        assertThat(data.orderStatus(order)).isEqualTo("PENDING_PAYMENT");

        // a short transfer: the webhook cannot confirm anything; payOS says UNDERPAID
        payos.pay(code(order), total(order) / 2, paidAt);
        deliver("raw-short", webhookFor(order, total(order) / 2, "FT-S", paidAt));
        assertThat(data.orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(data.paymentStatus(order)).isEqualTo("UNPAID");
        assertThat(row(order).get("provider_status")).isEqualTo("UNDERPAID");

        // the rest arrives but NO webhook is delivered: the return page's query recovers it
        payos.pay(code(order), total(order) - total(order) / 2, paidAt.plusSeconds(30));
        assertThat(flow.sync(order, PaymentReconciliationService.Trigger.CUSTOMER_RETURN)).isEqualTo(PaymentOutcome.CONFIRMED);
        assertThat(data.orderStatus(order)).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("PAYOS-06 payOS unreachable at the deadline: nothing is cancelled on stale evidence")
    void unreachablePayosCancelsNothing() {
        long order = order();
        start(order);
        data.ageOrder(order, 20);
        payos.down = true;

        assertThat(flow.sync(order, PaymentReconciliationService.Trigger.TIMEOUT_SCAN)).isEqualTo(PaymentOutcome.RECONCILING);

        assertThat(data.orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(data.cancelRestores(order)).isZero();
    }

    // ================================================================== late and cancelled

    @Test
    @DisplayName("PAYOS-07 money recorded by the bank after the deadline: cancelled, restocked once, REAL refund requested, never simulated")
    void latePaymentCancelsRestocksOnceAndRequestsARealRefund() {
        long order = order();
        start(order);
        LocalDateTime paidAt = created(order).plusMinutes(16);
        data.ageOrder(order, 20);
        payos.pay(code(order), total(order), LocalDateTime.now().minusMinutes(2)); // 18 min after creation: after 15
        int before = data.quantityAt(SERUM, "OS-THUDUC");

        assertThat(flow.sync(order, PaymentReconciliationService.Trigger.CUSTOMER_RETURN))
                .isEqualTo(PaymentOutcome.CANCELLED_LATE_PAYMENT);
        assertThat(flow.sync(order, PaymentReconciliationService.Trigger.CUSTOMER_RETURN))
                .isIn(PaymentOutcome.LATE_PAYMENT_REFUNDED, PaymentOutcome.ALREADY_CANCELLED);
        assertThat(flow.sync(order, PaymentReconciliationService.Trigger.TIMEOUT_SCAN))
                .isIn(PaymentOutcome.LATE_PAYMENT_REFUNDED, PaymentOutcome.ALREADY_CANCELLED);

        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.cancelRestores(order)).isEqualTo(1);
        assertThat(data.quantityAt(SERUM, "OS-THUDUC")).isEqualTo(before + 2); // restored exactly once
        Map<String, Object> row = row(order);
        assertThat(row.get("status")).isEqualTo("SUCCESS");       // the money was collected: kept as evidence
        assertThat(row.get("refund_status")).isEqualTo("MANUAL_REQUESTED");
        assertThat(data.paymentStatus(order)).isEqualTo("PAID");  // not REFUNDED until a real refund is confirmed
        assertThat(ledger(order)).isEqualTo(1);
        verifyNoInteractions(simulator);
        assertThat(paidAt).isAfter(created(order)); // (the bank time above is after the 15-minute window)
    }

    @Test
    @DisplayName("PAYOS-08 money arriving on an already cancelled order is recorded once, refunded once, and never revives the order")
    void moneyAfterCancellationIsNeverRevived() {
        long order = order();
        start(order);
        data.ageOrder(order, 20);
        assertThat(flow.sync(order, PaymentReconciliationService.Trigger.TIMEOUT_SCAN)).isEqualTo(PaymentOutcome.CANCELLED_TIMEOUT);
        assertThat(data.cancelRestores(order)).isEqualTo(1);
        verify(api).cancelLink(anyLong(), anyString()); // the link is closed; that is not a refund and proves nothing
        assertThat(row(order).get("status")).isEqualTo("FAILED");

        // the transfer lands anyway (it was already in flight when the link was closed)
        payos.status.put(code(order), "PENDING");
        payos.pay(code(order), total(order), LocalDateTime.now().minusMinutes(1));
        assertThat(flow.sync(order, PaymentReconciliationService.Trigger.CUSTOMER_RETURN)).isEqualTo(PaymentOutcome.LATE_PAYMENT_REFUNDED);
        assertThat(flow.sync(order, PaymentReconciliationService.Trigger.CUSTOMER_RETURN)).isEqualTo(PaymentOutcome.LATE_PAYMENT_REFUNDED);

        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.cancelRestores(order)).isEqualTo(1);
        assertThat(row(order).get("status")).isEqualTo("SUCCESS");
        assertThat(ledger(order)).isEqualTo(1);
        verifyNoInteractions(simulator);
    }

    @Test
    @DisplayName("PAYOS-09 an administrator confirms the real refund with the bank code: only then REFUNDED, once")
    void adminConfirmsTheRealRefundWithEvidence() {
        long order = order();
        start(order);
        data.ageOrder(order, 20);
        payos.pay(code(order), total(order), LocalDateTime.now().minusMinutes(2));
        flow.sync(order, PaymentReconciliationService.Trigger.CUSTOMER_RETURN);
        long refundId = jdbc.queryForObject("select refund_id from dbo.payment_refunds where order_id = ?", Long.class, order);

        assertThat(manualRefunds.getClass()).isNotNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> manualRefunds.confirm(order, refundId, "FT1", null))
                .isInstanceOfAny(org.springframework.security.access.AccessDeniedException.class,
                        org.springframework.security.core.AuthenticationException.class); // not an administrator
        assertThat(data.paymentStatus(order)).isEqualTo("PAID");

        assertThat(asAdmin(() -> manualRefunds.confirm(order, refundId, "FT26300077", "chuyển khoản")))
                .isEqualTo(ManualRefundService.Result.CONFIRMED);
        assertThat(asAdmin(() -> manualRefunds.confirm(order, refundId, "FT26300077", null)))
                .isEqualTo(ManualRefundService.Result.ALREADY_CONFIRMED);

        Map<String, Object> refund = jdbc.queryForMap("select * from dbo.payment_refunds where refund_id = ?", refundId);
        assertThat(refund.get("status")).isEqualTo("REFUNDED");
        assertThat(refund.get("evidence_reference")).isEqualTo("FT26300077");
        assertThat(refund.get("processed_by_user_id")).isNotNull();
        assertThat(row(order).get("refund_status")).isEqualTo("REFUNDED");
        assertThat(data.paymentStatus(order)).isEqualTo("REFUNDED");
        verifyNoInteractions(simulator);
    }

    // ================================================================== cancel and concurrency

    @Test
    @DisplayName("PAYOS-10 a customer cancel checks payOS first: money already paid is confirmed, then refunded as a real request")
    void cancelSeesPaymentThatNobodyRecordedYet() {
        long order = order();
        start(order);
        payos.pay(code(order), total(order), LocalDateTime.now().minusSeconds(30));
        flow.refresh(order); // what the controller does before cancelling

        var result = data.asRole(ALICE, "ROLE_CUSTOMER", () -> customerOrders.cancelOrder(ALICE, order));

        assertThat(result).isEqualTo(com.oneshop.service.payment.CustomerOrderCancellationService.Result.CANCELLED_REFUND_REQUESTED);
        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.cancelRestores(order)).isEqualTo(1);
        assertThat(row(order).get("refund_status")).isEqualTo("MANUAL_REQUESTED");
        assertThat(ledger(order)).isEqualTo(1);
        verifyNoInteractions(simulator);
    }

    @Test
    @DisplayName("PAYOS-11 a webhook and a cancel at the same instant never lose the money nor restock twice")
    void webhookAndCancelRace() throws Exception {
        for (int round = 0; round < 3; round++) {
            long order = order();
            start(order);
            LocalDateTime paidAt = LocalDateTime.now().minusSeconds(20);
            payos.pay(code(order), total(order), paidAt);
            payos.status.put(code(order), "PAID");
            flow.refresh(order); // the evidence is stored; the race is about applying it
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<?> hook = pool.submit(() -> deliver("raw-race-" + System.nanoTime(),
                        webhookFor(order, total(order), "FT-R" + System.nanoTime(), paidAt)));
                Future<?> cancel = pool.submit(() -> {
                    try {
                        data.asRole(ALICE, "ROLE_CUSTOMER", () -> customerOrders.cancelOrder(ALICE, order));
                    } catch (RuntimeException ignored) {
                        // a refused cancel is a legal outcome of the race
                    }
                });
                hook.get();
                cancel.get();
            } finally {
                pool.shutdownNow();
            }
            String status = data.orderStatus(order);
            assertThat(status).isIn("CONFIRMED", "CANCELLED");
            assertThat(data.cancelRestores(order)).isLessThanOrEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from dbo.payments where order_id = ? and status = 'SUCCESS'",
                    Integer.class, order)).isEqualTo(1); // collected exactly once
            if (status.equals("CANCELLED")) {
                assertThat(ledger(order)).isEqualTo(1);        // the money is owed back, once
                assertThat(data.cancelRestores(order)).isEqualTo(1);
            } else {
                assertThat(ledger(order)).isZero();
            }
            verify(simulator, never()).simulate(any());
        }
    }
}
