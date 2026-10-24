package com.oneshop;

import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.FulfillmentConflictException;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.CartService;
import com.oneshop.service.CheckoutService;
import com.oneshop.service.CustomerOrderService;
import com.oneshop.service.PaymentService;
import com.oneshop.service.fulfillment.FulfillmentAction;
import com.oneshop.service.fulfillment.OrderStatusAggregator;
import com.oneshop.service.fulfillment.StaffFulfillmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

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
import static com.oneshop.FulfillmentFixtures.STAFF_GOVAP;
import static com.oneshop.FulfillmentFixtures.STAFF_THUDUC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * V3-BE-04 with REAL, separately committed SQL Server transactions: double click, cancel against PREPARING, two
 * Stores working on the same Order, and faults in the middle of a step (everything must roll back). Every race has
 * a timeout; {@link SeedGuardV3} puts the test database back. Only against a {@code *_test} database.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=test-only-secret-test-only-secret-1234567890",
        "oneshop.checkout.online-enabled=true"})
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class FulfillmentConcurrencyDatabaseIntegrationTest {

    @Autowired
    StaffFulfillmentService staff;
    @Autowired
    CustomerOrderService customerOrders;
    @MockitoSpyBean
    PaymentService paymentService;
    @MockitoSpyBean
    OrderStatusAggregator aggregator;
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

    private void act(String staffEmail, long fulfillmentId, FulfillmentAction action) {
        act(staffEmail, fulfillmentId, action, null);
    }

    private void act(String staffEmail, long fulfillmentId, FulfillmentAction action, String input) {
        data.asStaff(staffEmail, () -> staff.perform(fulfillmentId, action, input));
    }

    private void cancel(long orderId) {
        data.asRole(ALICE, "ROLE_CUSTOMER", () -> {
            customerOrders.cancelOrder(ALICE, orderId);
            return null;
        });
    }

    /** Runs the tasks at the same instant; each result is {@code "ok"} or the exception it ended with. */
    private List<Object> race(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        task.call();
                        return "ok";
                    } catch (Exception ex) {
                        return ex;
                    }
                }));
            }
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private static long successes(List<Object> outcomes) {
        return outcomes.stream().filter("ok"::equals).count();
    }

    // ================================================================== races

    @Test
    @DisplayName("BE04-49 a double click on PREPARE: exactly one succeeds, one history row, no error for the Order")
    void be04_49_doubleClick() throws Exception {
        for (int round = 1; round <= 3; round++) {
            long order = data.codDelivery("OS-THUDUC", SERUM, 1);
            long fulfillment = data.onlyFulfillment(order);
            int historyBefore = data.fulfillmentHistory(fulfillment);
            int orderHistoryBefore = data.orderHistory(order);

            List<Object> outcomes = race(List.of(
                    () -> {
                        act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE);
                        return null;
                    },
                    () -> {
                        act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE);
                        return null;
                    }));

            assertThat(successes(outcomes)).as("round " + round + ": " + outcomes).isEqualTo(1);
            Object loser = outcomes.stream().filter(o -> !"ok".equals(o)).findFirst().orElseThrow();
            assertThat(loser).isInstanceOf(FulfillmentConflictException.class);
            assertThat(((FulfillmentConflictException) loser).getCode())
                    .isEqualTo(FulfillmentConflictException.STATUS_CHANGED);
            assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("PREPARING");
            assertThat(data.orderStatus(order)).isEqualTo("PROCESSING");
            assertThat(data.fulfillmentHistory(fulfillment)).isEqualTo(historyBefore + 1);
            assertThat(data.orderHistory(order)).isEqualTo(orderHistoryBefore + 1);
        }
    }

    @Test
    @DisplayName("BE04-50 customer cancel against Staff PREPARING: one wins, the state is never half cancelled")
    void be04_50_cancelAgainstPreparing() throws Exception {
        int cancelWon = 0;
        int prepareWon = 0;
        for (int round = 1; round <= 6; round++) {
            long order = data.codDelivery("OS-THUDUC", SERUM, 2);
            long fulfillment = data.onlyFulfillment(order);
            long movements = data.movements();

            List<Object> outcomes = race(List.of(
                    () -> {
                        cancel(order);
                        return null;
                    },
                    () -> {
                        act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE);
                        return null;
                    }));

            assertThat(successes(outcomes)).as("round " + round + ": " + outcomes).isEqualTo(1);
            String orderStatus = data.orderStatus(order);
            String fulfillmentStatus = data.fulfillmentStatus(fulfillment);
            int restores = jdbc.queryForObject("select count(*) from dbo.inventory_movements "
                    + "where reference_order_id = ? and type = 'CANCEL_ORDER'", Integer.class, order);
            if ("ok".equals(outcomes.get(0))) {
                cancelWon++;
                assertThat(orderStatus).isEqualTo("CANCELLED");
                assertThat(fulfillmentStatus).isEqualTo("CANCELLED");
                assertThat(restores).isEqualTo(1);
                assertThat(outcomes.get(1)).isInstanceOf(FulfillmentConflictException.class);
                assertThat(((FulfillmentConflictException) outcomes.get(1)).getCode())
                        .isEqualTo(FulfillmentConflictException.ORDER_CANCELLED);
            } else {
                prepareWon++;
                assertThat(orderStatus).isEqualTo("PROCESSING");
                assertThat(fulfillmentStatus).isEqualTo("PREPARING");
                assertThat(restores).isZero();
                assertThat(outcomes.get(0)).isInstanceOf(FulfillmentConflictException.class);
                assertThat(((FulfillmentConflictException) outcomes.get(0)).getCode())
                        .isEqualTo(FulfillmentConflictException.ORDER_ALREADY_PREPARING);
            }
            assertThat(data.movements()).isEqualTo(movements + restores);
        }
        assertThat(cancelWon + prepareWon).isEqualTo(6);
    }

    @Test
    @DisplayName("BE04-51 two Stores working on the same ONLINE Order at once: both succeed, one Order transition")
    void be04_51_twoStoresAtOnce() throws Exception {
        for (int round = 1; round <= 3; round++) {
            long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
            data.markPaid(order);
            long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
            long goVap = data.fulfillmentOf(order, "OS-GOVAP");
            int orderHistory = data.orderHistory(order);

            List<Object> outcomes = race(List.of(
                    () -> {
                        act(STAFF_THUDUC, thuDuc, FulfillmentAction.PREPARE);
                        return null;
                    },
                    () -> {
                        act(STAFF_GOVAP, goVap, FulfillmentAction.PREPARE);
                        return null;
                    }));

            assertThat(successes(outcomes)).as("round " + round + ": " + outcomes).isEqualTo(2);
            assertThat(data.fulfillmentStatus(thuDuc)).isEqualTo("PREPARING");
            assertThat(data.fulfillmentStatus(goVap)).isEqualTo("PREPARING");
            assertThat(data.orderStatus(order)).isEqualTo("PROCESSING");
            assertThat(data.orderHistory(order)).isEqualTo(orderHistory + 1);
        }
    }

    @Test
    @DisplayName("BE04-52 the last two parts completed at the same time: one COMPLETED transition, no double payment")
    void be04_52_lastTwoPartsCompleteTogether() throws Exception {
        for (int round = 1; round <= 3; round++) {
            long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
            data.markPaid(order);
            long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
            long goVap = data.fulfillmentOf(order, "OS-GOVAP");
            for (FulfillmentAction step : List.of(FulfillmentAction.PREPARE, FulfillmentAction.PACK,
                    FulfillmentAction.SHIP)) {
                act(STAFF_THUDUC, thuDuc, step);
                act(STAFF_GOVAP, goVap, step);
            }

            List<Object> outcomes = race(List.of(
                    () -> {
                        act(STAFF_THUDUC, thuDuc, FulfillmentAction.COMPLETE_DELIVERY);
                        return null;
                    },
                    () -> {
                        act(STAFF_GOVAP, goVap, FulfillmentAction.COMPLETE_DELIVERY);
                        return null;
                    }));

            assertThat(successes(outcomes)).as("round " + round + ": " + outcomes).isEqualTo(2);
            assertThat(data.orderStatus(order)).isEqualTo("COMPLETED");
            assertThat(data.successfulPayments(order)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from dbo.order_status_history where order_id = ? "
                    + "and new_status = 'COMPLETED'", Integer.class, order)).isEqualTo(1);
        }
    }

    // ================================================================== rollback

    @Test
    @DisplayName("BE04-53 COD collection failing at completion rolls the whole step back")
    void be04_53_collectionFailureRollsBack() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);
        for (FulfillmentAction step : List.of(FulfillmentAction.PREPARE, FulfillmentAction.PACK,
                FulfillmentAction.SHIP)) {
            act(STAFF_THUDUC, fulfillment, step);
        }
        int history = data.fulfillmentHistory(fulfillment);
        int orderHistory = data.orderHistory(order);
        PaymentService behindProxy = AopTestUtils.getUltimateTargetObject(paymentService);
        doThrow(new IllegalStateException("simulated collection failure"))
                .when(behindProxy).recordOfflineCollected(any(), any());

        assertThatThrownBy(() -> act(STAFF_THUDUC, fulfillment, FulfillmentAction.COMPLETE_DELIVERY))
                .isInstanceOf(IllegalStateException.class);

        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("SHIPPING");
        assertThat(data.orderStatus(order)).isEqualTo("SHIPPING");
        assertThat(data.paymentStatus(order)).isEqualTo("UNPAID");
        assertThat(data.successfulPayments(order)).isZero();
        assertThat(data.fulfillmentHistory(fulfillment)).isEqualTo(history);
        assertThat(data.orderHistory(order)).isEqualTo(orderHistory);
    }

    @Test
    @DisplayName("BE04-54 a failing Order aggregation rolls the Fulfillment change back too")
    void be04_54_aggregationFailureRollsBack() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);
        int history = data.fulfillmentHistory(fulfillment);
        OrderStatusAggregator behindProxy = AopTestUtils.getUltimateTargetObject(aggregator);
        doThrow(new IllegalStateException("simulated aggregation failure"))
                .when(behindProxy).recompute(any(), any(), any());

        assertThatThrownBy(() -> act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE))
                .isInstanceOf(IllegalStateException.class);

        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("CONFIRMED");
        assertThat(data.orderStatus(order)).isEqualTo("CONFIRMED");
        assertThat(data.fulfillmentHistory(fulfillment)).isEqualTo(history);
        assertThat(jdbc.queryForObject("select prepared_at from dbo.order_fulfillments where fulfillment_id = ?",
                Object.class, fulfillment)).isNull();
    }

    @Test
    @DisplayName("BE04-55 PAY_AT_STORE collection failing at pickup keeps the goods READY and the code valid")
    void be04_55_pickupCollectionFailureRollsBack() {
        long order = data.payAtStorePickup("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE);
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.MARK_READY);
        String code = data.pickupCode(fulfillment);
        PaymentService behindProxy = AopTestUtils.getUltimateTargetObject(paymentService);
        doThrow(new IllegalStateException("simulated collection failure"))
                .when(behindProxy).recordOfflineCollected(any(), any());

        assertThatThrownBy(() -> act(STAFF_THUDUC, fulfillment, FulfillmentAction.COMPLETE_PICKUP, code))
                .isInstanceOf(IllegalStateException.class);

        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("READY_FOR_PICKUP");
        assertThat(data.paymentStatus(order)).isEqualTo("UNPAID");
        assertThat(data.successfulPayments(order)).isZero();
        assertThat(jdbc.queryForObject("select picked_up_at from dbo.order_fulfillments where fulfillment_id = ?",
                Object.class, fulfillment)).isNull();
        assertThat(data.pickupCode(fulfillment)).isEqualTo(code);
    }

    @Test
    @DisplayName("BE04-56 a rejected step in a multi-Store Order leaves the other Fulfillment and the Order alone")
    void be04_56_rejectedStepLeavesOthersAlone() {
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        data.markPaid(order);
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        long goVap = data.fulfillmentOf(order, "OS-GOVAP");
        int orderHistory = data.orderHistory(order);

        assertThatThrownBy(() -> act(STAFF_THUDUC, thuDuc, FulfillmentAction.SHIP))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> act(STAFF_GOVAP, thuDuc, FulfillmentAction.PREPARE))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        assertThat(data.fulfillmentStatus(thuDuc)).isEqualTo("CONFIRMED");
        assertThat(data.fulfillmentStatus(goVap)).isEqualTo("CONFIRMED");
        assertThat(data.orderStatus(order)).isEqualTo("CONFIRMED");
        assertThat(data.orderHistory(order)).isEqualTo(orderHistory);
        Map<String, Long> counts = guard.counts();
        assertThat(counts.get("payments")).isGreaterThan(0);
    }
}
