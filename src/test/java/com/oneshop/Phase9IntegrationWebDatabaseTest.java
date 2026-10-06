package com.oneshop;

import com.oneshop.exception.BadRequestException;
import com.oneshop.service.DeliveryFulfillmentService;
import com.oneshop.service.PaymentService;
import com.oneshop.service.PickupFulfillmentService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.*;

import static com.oneshop.Phase9IntegrationScenario.*;
import static org.assertj.core.api.Assertions.*;

/** Real services/repositories/transactions and SQL Server; deliberately no Mockito overrides. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class Phase9IntegrationWebDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DeliveryFulfillmentService delivery;
    @Autowired PickupFulfillmentService pickup;
    @Autowired PaymentService payments;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired com.oneshop.service.CustomerOrderService customerOrders;
    @PersistenceContext EntityManager entityManager;
    @LocalServerPort int port;
    private Phase9IntegrationScenario scenario() { return new Phase9IntegrationScenario(jdbc, "http://localhost:" + port); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void tc15FullHttpLifecycleWithOptionalCatalogChanges(boolean changeCatalog) throws Exception { scenario().happy(changeCatalog); }
    @Test void mixedFailedPaymentRestoresOnlyItsOrderAndSiblingsStillCompleteOverHttp() throws Exception { scenario().mixedFailure(); }
    @Test void allThreeStaffScopesAndOtherCustomerStayIsolatedWithinSameCheckout() throws Exception { scenario().security(); }
    @Test void csrfEncodedPathsRolesAndCrossFlowPrepareAreEnforcedOverHttp() throws Exception { scenario().csrfAndRoles(); }
    @Test void multipleAssignmentsAllowOnlyActiveStoreAndUseAuthenticatedActor() throws Exception { scenario().multiAssignment(); }
    @ParameterizedTest @CsvSource({"DELIVERY,PAY_AT_STORE", "STORE_PICKUP,COD"})
    void invalidMatrixPairingsAreRejectedByBusinessAndActualSqlChecks(String type, String method) throws Exception { scenario().invalidCombination(type, method); }

    @Test void listQueryCountsStayConstantWhenThreeMoreStoreOrdersAreAddedAndDetailsRemainBounded() throws Exception {
        var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean enabled = statistics.isStatisticsEnabled(); statistics.setStatisticsEnabled(true);
        var s = scenario();
        try (var ignored = s.cleanup()) {
            long customerQueries = queryCount(statistics, () -> customerOrders.getOrders(CUSTOMER));
            long deliveryQueries = queryCount(statistics, () -> delivery.getDeliveryOrders(STAFF.get(1L)));
            long pickupQueries = queryCount(statistics, () -> pickup.getPickupOrders(STAFF.get(2L)));
            var f = s.mixed(); s.delivery(f); s.onlineResult(f, true); s.pickupReady(f, f.b(), 2); s.pickupComplete(f, f.b(), 2);
            s.pickupReady(f, f.c(), 4); s.pickupComplete(f, f.c(), 4);
            assertThat(queryCount(statistics, () -> customerOrders.getOrders(CUSTOMER))).as("Customer list does not add SQL per extra order/store").isEqualTo(customerQueries);
            assertThat(queryCount(statistics, () -> delivery.getDeliveryOrders(STAFF.get(1L)))).isEqualTo(deliveryQueries);
            assertThat(queryCount(statistics, () -> pickup.getPickupOrders(STAFF.get(2L)))).isEqualTo(pickupQueries);
            for (long id : f.ids()) assertThat(queryCount(statistics, () -> customerOrders.getOrder(CUSTOMER, id))).isBetween(1L, 12L);
            assertThat(queryCount(statistics, () -> delivery.getDeliveryOrder(STAFF.get(1L), f.a()))).isBetween(1L, 12L);
            assertThat(queryCount(statistics, () -> pickup.getPickupOrder(STAFF.get(2L), f.b()))).isBetween(1L, 12L);
            assertThat(queryCount(statistics, () -> pickup.getPickupOrder(STAFF.get(4L), f.c()))).isBetween(1L, 12L);
        } finally { statistics.setStatisticsEnabled(enabled); }
    }
    private static long queryCount(org.hibernate.stat.Statistics statistics, Runnable read) {
        long before = statistics.getPrepareStatementCount(); read.run(); return statistics.getPrepareStatementCount() - before;
    }

    private static final class Abort extends RuntimeException { }
    @ParameterizedTest @ValueSource(strings = {"FAILED_RESTORE", "COD_COMPLETE", "PICKUP_READY", "PAY_AT_STORE_COMPLETE"})
    void composedSubsystemWritesRollbackTogetherIncludingSiblings(String stage) throws Exception {
        var s = scenario();
        try (var ignored = s.cleanup()) {
            var f = s.mixed(); long p = 0;
            switch (stage) {
                case "FAILED_RESTORE" -> p = payments.createOnlinePaymentAttempt(CUSTOMER, f.b()).paymentId();
                case "COD_COMPLETE" -> { prepareDelivery(f.a()); delivery.markDeliveryShipping(STAFF.get(1L), f.a()); }
                case "PICKUP_READY" -> pickup.startPreparingPickup(STAFF.get(4L), f.c());
                case "PAY_AT_STORE_COMPLETE" -> readyPickup(f.c());
                default -> throw new AssertionError(stage);
            }
            long attempt = p; var before = s.snapshot();
            assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                switch (stage) {
                    case "FAILED_RESTORE" -> {
                        payments.markOnlinePaymentFailed(CUSTOMER, f.b(), attempt); entityManager.flush();
                        assertThat(s.order(f.b())).containsEntry("order_status", "CANCELLED").containsEntry("payment_status", "FAILED");
                        assertThat(jdbc.queryForObject("select count(*) from dbo.inventory_movements where reference_order_id=? and type='CANCEL_ORDER'", Integer.class, f.b())).isEqualTo(1);
                        assertThat(s.receipts(f.b()).get(0)).containsEntry("status", "FAILED");
                    }
                    case "COD_COMPLETE" -> {
                        delivery.completeDelivery(STAFF.get(1L), f.a()); entityManager.flush();
                        assertThat(s.order(f.a())).containsEntry("order_status", "COMPLETED").containsEntry("payment_status", "PAID");
                        assertThat(s.receipts(f.a())).hasSize(1); assertThat(s.history(f.a())).hasSize(5);
                    }
                    case "PICKUP_READY" -> {
                        pickup.markReadyForPickup(STAFF.get(4L), f.c()); entityManager.flush();
                        assertThat(s.order(f.c())).containsEntry("order_status", "READY_FOR_PICKUP");
                        assertThat(s.code(f.c())).matches("[A-HJ-NP-Z2-9]{8}"); assertThat(s.order(f.c()).get("ready_at")).isNotNull();
                    }
                    case "PAY_AT_STORE_COMPLETE" -> {
                        pickup.completePickup(STAFF.get(4L), f.c(), s.code(f.c())); entityManager.flush();
                        assertThat(s.order(f.c())).containsEntry("order_status", "COMPLETED").containsEntry("payment_status", "PAID");
                        assertThat(s.order(f.c()).get("picked_up_at")).isNotNull(); assertThat(s.receipts(f.c())).hasSize(1); assertThat(s.history(f.c())).hasSize(4);
                    }
                    default -> throw new AssertionError(stage);
                }
                // JDBC above observes actual SQL writes through the same real transaction, then aborts its commit.
                throw new Abort();
            })).isExactlyInstanceOf(Abort.class);
            assertThat(s.snapshot()).as("Every SQL row/field/timestamp restored after outer rollback").isEqualTo(before);
        }
    }
    private void prepareDelivery(long id) { delivery.startPreparingDelivery(STAFF.get(1L), id); delivery.markDeliveryPacked(STAFF.get(1L), id); }
    private void readyPickup(long id) { pickup.startPreparingPickup(STAFF.get(4L), id); pickup.markReadyForPickup(STAFF.get(4L), id); }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(15, TimeUnit.SECONDS)).as("Independent Order must commit while sibling transaction remains open").isTrue(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
    @Test void pickupSiblingCommitsWhileDeliveryPackTransactionRemainsOpen() throws Exception {
        var s = scenario();
        try (var ignored = s.cleanup()) {
            var f = s.mixed(); delivery.startPreparingDelivery(STAFF.get(1L), f.a()); pickup.startPreparingPickup(STAFF.get(4L), f.c());
            var untouchedB = s.aggregate(f.b()); var stock = s.rows("store_products"); var movements = s.rows("inventory_movements");
            var grouping = jdbc.queryForMap("select * from dbo.checkout_sessions where checkout_id=?", f.checkout());
            CountDownLatch packedButUncommitted = new CountDownLatch(1), readyCommitted = new CountDownLatch(1);
            var pool = Executors.newFixedThreadPool(2);
            try {
                Future<?> a = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                    delivery.markDeliveryPacked(STAFF.get(1L), f.a()); entityManager.flush();
                    assertThat(s.order(f.a())).containsEntry("order_status", "PACKED");
                    packedButUncommitted.countDown(); await(readyCommitted);
                    assertThat(s.order(f.c())).containsEntry("order_status", "READY_FOR_PICKUP");
                }));
                Future<?> c = pool.submit(() -> { await(packedButUncommitted); pickup.markReadyForPickup(STAFF.get(4L), f.c()); readyCommitted.countDown(); });
                c.get(30, TimeUnit.SECONDS); a.get(30, TimeUnit.SECONDS);
            } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue(); }
            assertThat(s.order(f.a())).containsEntry("order_status", "PACKED"); assertThat(s.order(f.c())).containsEntry("order_status", "READY_FOR_PICKUP");
            assertThat(s.history(f.a())).hasSize(3); assertThat(s.history(f.c())).hasSize(3);
            assertThat(s.aggregate(f.b())).isEqualTo(untouchedB); assertThat(s.receipts(f.a())).isEmpty(); assertThat(s.receipts(f.c())).isEmpty();
            assertThat(s.rows("store_products")).isEqualTo(stock); assertThat(s.rows("inventory_movements")).isEqualTo(movements);
            assertThat(jdbc.queryForMap("select * from dbo.checkout_sessions where checkout_id=?", f.checkout())).isEqualTo(grouping);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"PAYMENT_RESULT", "DELIVERY_PREPARE", "COD_COMPLETE", "PICKUP_READY", "PICKUP_COMPLETE"})
    void sameOrderRepresentativeRacesKeepSiblingAggregatesAndNeverDuplicateReceiptsOrHistory(String stage) throws Exception {
        var s = scenario();
        try (var ignored = s.cleanup()) {
            var f = s.mixed(); long id = stage.equals("PAYMENT_RESULT") ? f.b() : stage.startsWith("PICKUP") ? f.c() : f.a();
            long attempt = stage.equals("PAYMENT_RESULT") ? payments.createOnlinePaymentAttempt(CUSTOMER, id).paymentId() : 0;
            if (stage.equals("COD_COMPLETE")) { prepareDelivery(id); delivery.markDeliveryShipping(STAFF.get(1L), id); }
            if (stage.equals("PICKUP_READY")) pickup.startPreparingPickup(STAFF.get(4L), id);
            if (stage.equals("PICKUP_COMPLETE")) readyPickup(id);
            String code = s.code(id); var siblings = new LinkedHashMap<Long, Map<String, Object>>();
            f.ids().stream().filter(v -> v != id).forEach(v -> siblings.put(v, s.aggregate(v)));
            int beforeHistory = s.history(id).size(); CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
            var pool = Executors.newFixedThreadPool(2);
            try {
                List<Future<Boolean>> outcomes = new ArrayList<>();
                for (int index = 0; index < 2; index++) {
                    int side = index;
                    outcomes.add(pool.submit(() -> {
                        ready.countDown(); await(start);
                        try {
                            switch (stage) {
                                case "PAYMENT_RESULT" -> { if (side == 0) payments.markOnlinePaymentSuccess(CUSTOMER, id, attempt); else payments.markOnlinePaymentFailed(CUSTOMER, id, attempt); }
                                case "DELIVERY_PREPARE" -> delivery.startPreparingDelivery(STAFF.get(1L), id);
                                case "COD_COMPLETE" -> delivery.completeDelivery(STAFF.get(1L), id);
                                case "PICKUP_READY" -> pickup.markReadyForPickup(STAFF.get(4L), id);
                                case "PICKUP_COMPLETE" -> pickup.completePickup(STAFF.get(4L), id, code);
                                default -> throw new AssertionError(stage);
                            }
                            return true;
                        } catch (BadRequestException expectedLoser) { return false; }
                    }));
                }
                await(ready); start.countDown();
                assertThat(List.of(outcomes.get(0).get(30, TimeUnit.SECONDS), outcomes.get(1).get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue(); }
            assertThat(s.history(id)).hasSize(beforeHistory + 1);
            if (stage.equals("PICKUP_READY")) { assertThat(s.code(id)).matches("[A-HJ-NP-Z2-9]{8}"); assertThat(s.receipts(id)).isEmpty(); }
            else if (!stage.equals("DELIVERY_PREPARE")) assertThat(s.receipts(id)).hasSize(1);
            if (stage.equals("PICKUP_COMPLETE")) assertThat(s.order(id).get("picked_up_at")).isNotNull();
            if (stage.equals("PAYMENT_RESULT")) assertThat(jdbc.queryForObject("select count(*) from dbo.inventory_movements where reference_order_id=? and type='CANCEL_ORDER'", Integer.class, id))
                    .isEqualTo(s.order(id).get("order_status").equals("CANCELLED") ? 1 : 0);
            siblings.forEach((v, prior) -> assertThat(s.aggregate(v)).isEqualTo(prior));
        }
    }
}
