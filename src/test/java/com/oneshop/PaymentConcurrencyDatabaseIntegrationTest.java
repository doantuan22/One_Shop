package com.oneshop;

import com.oneshop.exception.PaymentConflictException;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.CartService;
import com.oneshop.service.CheckoutService;
import com.oneshop.service.CustomerOrderService;
import com.oneshop.service.InventoryService;
import com.oneshop.service.fulfillment.FulfillmentAction;
import com.oneshop.service.fulfillment.StaffFulfillmentService;
import com.oneshop.service.payment.AdminPaymentService;
import com.oneshop.service.payment.OrderStatusWriter;
import com.oneshop.service.payment.PaymentOutcome;
import com.oneshop.service.payment.PaymentReconciliationService;
import com.oneshop.service.payment.PaymentReconciliationService.SimulatedResult;
import com.oneshop.service.payment.PaymentTimeoutService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.oneshop.FulfillmentFixtures.ALICE;
import static com.oneshop.FulfillmentFixtures.SERUM;
import static com.oneshop.FulfillmentFixtures.SHAMPOO;
import static com.oneshop.FulfillmentFixtures.STAFF_THUDUC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * V3-BE-05 with REAL, separately committed SQL Server transactions: the races of plan section 5.5 (callback vs timeout,
 * cancel vs payment, cancel vs Staff PREPARING, duplicates, two workers, Admin vs worker vs late callback), faults in
 * the middle of a decision (everything rolls back) and recovery after a restart (a second application context).
 *
 * <p>Every race has a timeout and runs several rounds; {@link SeedGuardV3} puts the test database back. Only against
 * a {@code *_test} database.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=test-only-secret-test-only-secret-1234567890",
        "oneshop.checkout.online-enabled=true"})
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class PaymentConcurrencyDatabaseIntegrationTest {

    static final String ADMIN = "admin@oneshop.vn";

    @Autowired
    PaymentReconciliationService reconciliation;
    @Autowired
    PaymentTimeoutService timeouts;
    @Autowired
    AdminPaymentService adminPayments;
    @Autowired
    CustomerOrderService customerOrders;
    @Autowired
    StaffFulfillmentService staff;
    @MockitoSpyBean
    InventoryService inventoryService;
    @MockitoSpyBean
    OrderStatusWriter statusWriter;
    @Autowired
    CartService cartService;
    @Autowired
    CheckoutService checkoutService;
    @Autowired
    CartRepository cartRepository;
    @Autowired
    CartItemRepository cartItemRepository;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    StoreRepository storeRepository;
    @Autowired
    StoreProductRepository storeProductRepository;
    @Autowired
    JdbcTemplate jdbc;

    private SeedGuardV3 guard;
    private FulfillmentFixtures data;
    private boolean remembered;

    @BeforeEach
    void remember() {
        guard = new SeedGuardV3(jdbc);
        assumeTrue(guard.isTestDatabase(), "these tests commit: run with DB_NAME=oneshop_test");
        guard.remember();
        remembered = true;
        data = new FulfillmentFixtures(jdbc, cartService, checkoutService, cartRepository, cartItemRepository,
                productRepository, storeRepository, storeProductRepository);
    }

    @AfterEach
    void restore() {
        if (remembered) {
            guard.restore();
        }
    }

    // ------------------------------------------------------------------ helpers

    private Object simulate(long orderId, SimulatedResult scenario) {
        return reconciliation.simulate(ALICE, orderId, data.attemptId(orderId), scenario);
    }

    private void cancel(long orderId) {
        data.asRole(ALICE, "ROLE_CUSTOMER", () -> {
            customerOrders.cancelOrder(ALICE, orderId);
            return null;
        });
    }

    private List<Object> race(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        Object value = task.call();
                        return value == null ? "ok" : value;
                    } catch (Exception ex) {
                        return ex;
                    }
                }));
            }
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(90, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private static void assertNoUnexpectedErrors(List<Object> outcomes) {
        assertThat(outcomes).as("outcomes " + outcomes).noneMatch(o -> o instanceof Exception
                && !(o instanceof PaymentConflictException)
                && !(o instanceof com.oneshop.exception.FulfillmentConflictException));
    }

    private int refundedRows(long orderId) {
        return jdbc.queryForObject("select count(*) from dbo.payments where order_id = ? "
                + "and refund_status = 'SIMULATED_REFUNDED'", Integer.class, orderId);
    }

    private int confirmedHistory(long orderId) {
        return jdbc.queryForObject("select count(*) from dbo.order_status_history where order_id = ? "
                + "and new_status = 'CONFIRMED'", Integer.class, orderId);
    }

    // ================================================================== races

    @Test
    @DisplayName("BE05-38 SUCCESS vs TIMEOUT on an expired Order: cancelled once, restored once, refunded once, never confirmed")
    void be05_38_successVsTimeout() throws Exception {
        for (int round = 1; round <= 5; round++) {
            long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
            data.ageOrder(order, 16);

            List<Object> outcomes = race(List.of(
                    () -> simulate(order, SimulatedResult.SUCCESS),
                    () -> timeouts.scan()));

            assertNoUnexpectedErrors(outcomes);
            assertThat(data.orderStatus(order)).as("round " + round).isEqualTo("CANCELLED");
            assertThat(data.cancelRestores(order)).isEqualTo(1);
            assertThat(data.quantityAt(SERUM, "OS-THUDUC")).isEqualTo(22);
            assertThat(data.successfulPayments(order)).isEqualTo(1);
            assertThat(refundedRows(order)).isEqualTo(1);
            assertThat(data.paymentStatus(order)).isEqualTo("REFUNDED");
            assertThat(confirmedHistory(order)).isZero();
        }
    }

    @Test
    @DisplayName("BE05-39 SUCCESS in time vs the scan: the Order is confirmed and never cancelled")
    void be05_39_successInTimeVsScan() throws Exception {
        for (int round = 1; round <= 3; round++) {
            long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
            data.ageOrder(order, 8);

            List<Object> outcomes = race(List.of(
                    () -> simulate(order, SimulatedResult.SUCCESS),
                    () -> timeouts.scan()));

            assertNoUnexpectedErrors(outcomes);
            assertThat(data.orderStatus(order)).as("round " + round).isEqualTo("CONFIRMED");
            assertThat(data.cancelRestores(order)).isZero();
            assertThat(data.successfulPayments(order)).isEqualTo(1);
            assertThat(confirmedHistory(order)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("BE05-40 CANCEL vs PAYMENT SUCCESS: always CANCELLED, restored once, one collection, refunded once")
    void be05_40_cancelVsPayment() throws Exception {
        for (int round = 1; round <= 6; round++) {
            long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
            data.ageOrder(order, 4);

            List<Object> outcomes = race(List.of(
                    () -> {
                        cancel(order);
                        return "cancelled";
                    },
                    () -> simulate(order, SimulatedResult.SUCCESS)));

            assertNoUnexpectedErrors(outcomes);
            assertThat(data.orderStatus(order)).as("round " + round + " " + outcomes).isEqualTo("CANCELLED");
            assertThat(data.cancelRestores(order)).isEqualTo(1);
            assertThat(data.quantityAt(SERUM, "OS-THUDUC")).isEqualTo(22);
            assertThat(data.successfulPayments(order)).isLessThanOrEqualTo(1);
            if (data.successfulPayments(order) == 1) {
                // the money was collected: it is refunded exactly once, never left on a cancelled Order
                assertThat(refundedRows(order)).isEqualTo(1);
                assertThat(data.paymentStatus(order)).isEqualTo("REFUNDED");
            } else {
                assertThat(data.paymentStatus(order)).isEqualTo("UNPAID");
            }
        }
    }

    @Test
    @DisplayName("BE05-41 customer CANCEL vs Staff PREPARING on a paid Order: one winner, never half cancelled")
    void be05_41_cancelVsPreparing() throws Exception {
        for (int round = 1; round <= 5; round++) {
            long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
            simulate(order, SimulatedResult.SUCCESS);
            long part = data.fulfillmentOf(order, "OS-THUDUC");

            List<Object> outcomes = race(List.of(
                    () -> {
                        cancel(order);
                        return "cancelled";
                    },
                    () -> {
                        data.asStaff(STAFF_THUDUC, () -> staff.perform(part, FulfillmentAction.PREPARE, null));
                        return "prepared";
                    }));

            long winners = outcomes.stream().filter(o -> o instanceof String).count();
            assertThat(winners).as("round " + round + " " + outcomes).isEqualTo(1);
            if ("cancelled".equals(outcomes.get(0))) {
                assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
                assertThat(data.fulfillments(order)).allMatch(f -> f.status().equals("CANCELLED"));
                assertThat(data.cancelRestores(order)).isEqualTo(2);
                assertThat(refundedRows(order)).isEqualTo(1);
                assertThat(data.paymentStatus(order)).isEqualTo("REFUNDED");
            } else {
                assertThat(data.orderStatus(order)).isEqualTo("PROCESSING");
                assertThat(data.fulfillmentStatus(part)).isEqualTo("PREPARING");
                assertThat(data.cancelRestores(order)).isZero();
                assertThat(refundedRows(order)).isZero();
                assertThat(data.paymentStatus(order)).isEqualTo("PAID");
            }
        }
    }

    @Test
    @DisplayName("BE05-42 duplicate callbacks at once: one collection, one CONFIRMED history row")
    void be05_42_duplicateCallbacks() throws Exception {
        for (int round = 1; round <= 3; round++) {
            long order = data.onlinePickup("OS-THUDUC", SERUM, 1);

            List<Object> outcomes = race(List.of(
                    () -> simulate(order, SimulatedResult.SUCCESS),
                    () -> simulate(order, SimulatedResult.SUCCESS),
                    () -> simulate(order, SimulatedResult.SUCCESS)));

            assertNoUnexpectedErrors(outcomes);
            assertThat(outcomes.stream().filter(PaymentOutcome.CONFIRMED::equals).count()).as("" + outcomes)
                    .isEqualTo(1);
            assertThat(data.successfulPayments(order)).isEqualTo(1);
            assertThat(confirmedHistory(order)).isEqualTo(1);
            assertThat(data.orderStatus(order)).isEqualTo("CONFIRMED");
        }
    }

    @Test
    @DisplayName("BE05-43 duplicate cancel requests at once: one restoration, one refund")
    void be05_43_duplicateCancel() throws Exception {
        for (int round = 1; round <= 3; round++) {
            long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
            simulate(order, SimulatedResult.SUCCESS);

            List<Object> outcomes = race(List.of(
                    () -> {
                        cancel(order);
                        return "ok";
                    },
                    () -> {
                        cancel(order);
                        return "ok";
                    },
                    () -> {
                        cancel(order);
                        return "ok";
                    }));

            assertThat(outcomes).as("" + outcomes).allMatch("ok"::equals);
            assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
            assertThat(data.cancelRestores(order)).isEqualTo(1);
            assertThat(data.quantityAt(SERUM, "OS-THUDUC")).isEqualTo(22);
            assertThat(refundedRows(order)).isEqualTo(1);
            assertThat(data.paymentStatus(order)).isEqualTo("REFUNDED");
        }
    }

    @Test
    @DisplayName("BE05-44 two timeout scans at once: every expired Order cancelled once and restored once")
    void be05_44_twoScans() throws Exception {
        List<Long> orders = new ArrayList<>();
        orders.add(data.onlinePickup("OS-THUDUC", SERUM, 2));
        orders.add(data.onlinePickup("OS-THUDUC", SHAMPOO, 1));
        orders.add(data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO));
        orders.forEach(id -> data.ageOrder(id, 16));

        List<Object> outcomes = race(List.of(() -> timeouts.scan(), () -> timeouts.scan(), () -> timeouts.scan()));

        assertNoUnexpectedErrors(outcomes);
        for (long order : orders) {
            assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        }
        assertThat(data.cancelRestores(orders.get(0))).isEqualTo(1);
        assertThat(data.cancelRestores(orders.get(1))).isEqualTo(1);
        assertThat(data.cancelRestores(orders.get(2))).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_status_history where order_id in (?, ?, ?) "
                + "and new_status = 'CANCELLED'", Integer.class, orders.get(0), orders.get(1), orders.get(2)))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("BE05-45 scan vs Admin re-check vs late callback on an unapplied late collection: one outcome")
    void be05_45_scanAdminCallback() throws Exception {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
        simulate(order, SimulatedResult.UNKNOWN);
        data.ageOrder(order, 31);
        timeouts.scan();
        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION");
        // the gateway settles: money was collected after the deadline (21 minutes after creation)
        jdbc.update("update dbo.payments set paid_at = ?, transaction_code = replace(transaction_code, '~UNKNOWN', '') "
                + "where order_id = ?", LocalDateTime.now().minusMinutes(10).withNano(0), order);

        List<Object> outcomes = race(List.of(
                () -> timeouts.scan(),
                () -> data.asRole(ADMIN, "ROLE_ADMIN", () -> adminPayments.recheck(order)),
                () -> simulate(order, SimulatedResult.SUCCESS)));

        assertNoUnexpectedErrors(outcomes);
        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.cancelRestores(order)).isEqualTo(1);
        assertThat(data.quantityAt(SERUM, "OS-THUDUC")).isEqualTo(22);
        assertThat(data.successfulPayments(order)).isEqualTo(1);
        assertThat(refundedRows(order)).isEqualTo(1);
    }

    // ================================================================== rollback

    @Test
    @DisplayName("BE05-46 a failing stock restoration rolls the whole cancellation back and does not stop the scan")
    void be05_46_restoreFailureRollsBack() {
        long first = data.onlinePickup("OS-THUDUC", SERUM, 2);
        long second = data.onlinePickup("OS-THUDUC", SHAMPOO, 1);
        data.ageOrder(first, 16);
        data.ageOrder(second, 16);
        InventoryService behindProxy = AopTestUtils.getUltimateTargetObject(inventoryService);
        doThrow(new IllegalStateException("simulated stock failure")).doCallRealMethod()
                .when(behindProxy).restoreForCancelledOrder(any(), any());
        int serum = data.quantityAt(SERUM, "OS-THUDUC");
        int shampoo = data.quantityAt(SHAMPOO, "OS-THUDUC");

        PaymentTimeoutService.ScanResult scan = timeouts.scan();

        assertThat(scan.failed()).isEqualTo(1);
        // exactly one of the two failed whole: nothing of it changed
        List<Long> failed = List.of(first, second).stream()
                .filter(id -> data.orderStatus(id).equals("PENDING_PAYMENT")).toList();
        assertThat(failed).hasSize(1);
        long untouched = failed.get(0);
        assertThat(data.orderStatus(untouched)).isEqualTo("PENDING_PAYMENT");
        assertThat(data.fulfillments(untouched)).allMatch(f -> f.status().equals("CONFIRMED"));
        assertThat(data.payment(data.attemptId(untouched)).get("status")).isEqualTo("PENDING");
        assertThat(data.cancelRestores(untouched)).isZero();
        long done = untouched == first ? second : first;
        assertThat(data.orderStatus(done)).isEqualTo("CANCELLED");
        assertThat(data.cancelRestores(done)).isEqualTo(1);
        assertThat(data.quantityAt(SERUM, "OS-THUDUC") + data.quantityAt(SHAMPOO, "OS-THUDUC"))
                .isEqualTo(serum + shampoo + (untouched == first ? 1 : 2));

        // the next scan finishes the one that failed, once
        org.mockito.Mockito.reset(behindProxy);
        timeouts.scan();
        assertThat(data.orderStatus(untouched)).isEqualTo("CANCELLED");
        assertThat(data.cancelRestores(untouched)).isEqualTo(1);
    }

    @Test
    @DisplayName("BE05-47 a failure while writing the Order status rolls the payment result back too")
    void be05_47_statusWriteFailureRollsBack() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);
        OrderStatusWriter behindProxy = AopTestUtils.getUltimateTargetObject(statusWriter);
        doThrow(new IllegalStateException("simulated status write failure")).when(behindProxy)
                .write(any(), any(), any(), any());

        assertThatThrownBy(() -> simulate(order, SimulatedResult.SUCCESS)).isInstanceOf(IllegalStateException.class);

        assertThat(data.orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(data.paymentStatus(order)).isEqualTo("UNPAID");
        Map<String, Object> attempt = data.payments(order).get(0);
        assertThat(attempt.get("status")).isEqualTo("PENDING");
        assertThat(attempt.get("paid_at")).isNull(); // the capture was rolled back with the rest
        org.mockito.Mockito.reset(behindProxy);
        assertThat(simulate(order, SimulatedResult.SUCCESS)).isEqualTo(PaymentOutcome.CONFIRMED);
    }

    // ================================================================== restart and worker

    @Test
    @DisplayName("BE05-48 restart recovery: a second application context continues from the database alone")
    void be05_48_restartRecovery() {
        long unknown = data.onlinePickup("OS-THUDUC", SERUM, 2);
        long unapplied = data.onlinePickup("OS-THUDUC", SHAMPOO, 1);
        long expired = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        simulate(unknown, SimulatedResult.UNKNOWN);
        data.ageOrder(unknown, 20);
        data.ageOrder(unapplied, 20);
        jdbc.update("update dbo.payments set paid_at = ? where order_id = ? and status = 'PENDING'",
                LocalDateTime.now().minusMinutes(18).withNano(0), unapplied); // collected, never applied
        data.ageOrder(expired, 40);

        // "restart": a brand-new application with empty memory; nothing is shared with this test's context
        try (ConfigurableApplicationContext restarted = newApplication(false)) {
            PaymentTimeoutService.ScanResult scan = restarted.getBean(PaymentTimeoutService.class).scan();
            assertThat(scan.failed()).isZero();
        }

        assertThat(data.orderStatus(unknown)).isEqualTo("PENDING_PAYMENT"); // still reconciling, doubt kept
        assertThat(data.payments(unknown).get(0).get("transaction_code").toString()).endsWith("~UNKNOWN");
        assertThat(data.orderStatus(unapplied)).isEqualTo("CONFIRMED");
        assertThat(data.paymentStatus(unapplied)).isEqualTo("PAID");
        assertThat(data.orderStatus(expired)).isEqualTo("CANCELLED");
        assertThat(data.cancelRestores(expired)).isEqualTo(2);

        // 31 minutes later another fresh application moves the doubtful one to the administrator
        data.ageOrder(unknown, 31);
        try (ConfigurableApplicationContext again = newApplication(false)) {
            again.getBean(PaymentTimeoutService.class).scan();
        }
        assertThat(data.orderStatus(unknown)).isEqualTo("NEEDS_ATTENTION");
        assertThat(data.cancelRestores(unknown)).isZero();
    }

    @Test
    @DisplayName("BE05-49 the scheduled worker cancels an expired Order by itself within a few scan intervals")
    void be05_49_scheduledWorker() throws Exception {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
        data.ageOrder(order, 16);

        try (ConfigurableApplicationContext worker = newApplication(true)) {
            long deadline = System.currentTimeMillis() + 40_000;
            while (System.currentTimeMillis() < deadline && !"CANCELLED".equals(data.orderStatus(order))) {
                Thread.sleep(500);
            }
        }

        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.cancelRestores(order)).isEqualTo(1);
    }

    /** A second, independent application against the same database (web server on a random port). */
    private ConfigurableApplicationContext newApplication(boolean workerOn) {
        // command-line arguments outrank the surefire system property that switches the worker off for the suite
        return new SpringApplicationBuilder(OneShopApplication.class)
                .profiles("dev")
                .run("--server.port=0", "--app.jwt.secret=test-only-secret-test-only-secret-1234567890",
                        "--oneshop.payment.timeout-worker-enabled=" + workerOn,
                        "--oneshop.payment.timeout-scan-interval-ms=1000",
                        "--spring.main.banner-mode=off");
    }
}
