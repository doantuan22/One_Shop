package com.oneshop;

import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.FulfillmentConflictException;
import com.oneshop.exception.PaymentConflictException;
import com.oneshop.exception.ResourceNotFoundException;
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
import com.oneshop.service.fulfillment.StaffFulfillmentService;
import com.oneshop.service.payment.AdminPaymentService;
import com.oneshop.service.payment.PaymentGateway;
import com.oneshop.service.payment.PaymentOutcome;
import com.oneshop.service.payment.PaymentReconciliationService;
import com.oneshop.service.payment.PaymentReconciliationService.SimulatedResult;
import com.oneshop.service.payment.PaymentTimeoutService;
import com.oneshop.service.payment.RefundService;
import com.oneshop.service.payment.RefundSimulator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.oneshop.FulfillmentFixtures.ALICE;
import static com.oneshop.FulfillmentFixtures.BOB;
import static com.oneshop.FulfillmentFixtures.SERUM;
import static com.oneshop.FulfillmentFixtures.SHAMPOO;
import static com.oneshop.FulfillmentFixtures.STAFF_THUDUC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * V3-BE-05 on the real SQL Server: the mock gateway ledger kept in {@code payments}, the 15-minute deadline, the
 * reconciliation window, verified FAILED, cancel-and-restore, the simulated refund and the Admin resolution. Orders
 * are made by the real checkout; the payment deadline is moved by moving {@code orders.created_at}.
 *
 * <p>These tests commit; {@link SeedGuardV3} puts the test database back. Only against a {@code *_test} database.
 * {@code oneshop.checkout.online-enabled=true} is a TEST-ONLY switch (ONLINE checkout is closed by default).
 */
@SpringBootTest(properties = {
        "app.jwt.secret=test-only-secret-test-only-secret-1234567890",
        "oneshop.checkout.online-enabled=true"})
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class PaymentSettlementDatabaseIntegrationTest {

    static final String ADMIN = "admin@example.com";

    @Autowired
    PaymentReconciliationService reconciliation;
    @Autowired
    PaymentTimeoutService timeouts;
    @Autowired
    AdminPaymentService adminPayments;
    @Autowired
    CustomerOrderService customerOrders;
    @Autowired
    PaymentService paymentService;
    @Autowired
    StaffFulfillmentService staff;
    @MockitoSpyBean
    RefundSimulator refundSimulator;
    @MockitoSpyBean
    PaymentGateway gateway;
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

    private PaymentOutcome simulate(long orderId, SimulatedResult scenario) {
        return reconciliation.simulate(ALICE, orderId, data.attemptId(orderId), scenario);
    }

    private void cancel(long orderId) {
        data.asRole(ALICE, "ROLE_CUSTOMER", () -> {
            customerOrders.cancelOrder(ALICE, orderId);
            return null;
        });
    }

    private <T> T asAdmin(java.util.function.Supplier<T> action) {
        return data.asRole(ADMIN, "ROLE_ADMIN", action);
    }

    private int history(long orderId) {
        return data.orderHistory(orderId);
    }

    private String lastNote(long orderId) {
        return jdbc.queryForObject("select top 1 note from dbo.order_status_history where order_id = ? "
                + "order by history_id desc", String.class, orderId);
    }

    private String code(long orderId) {
        return (String) data.payments(orderId).get(0).get("transaction_code");
    }

    /** Puts the unapplied "collected at" time on the attempt, as a crash between collection and application would. */
    private void captureAt(long orderId, LocalDateTime at) {
        jdbc.update("update dbo.payments set paid_at = ? where order_id = ? and status = 'PENDING'", at, orderId);
    }

    private <T extends Throwable> T conflict(Class<T> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable ex) {
            if (type.isInstance(ex)) {
                return type.cast(ex);
            }
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + ex, ex);
        }
        throw new AssertionError("expected " + type.getSimpleName());
    }

    // ================================================================== A. gateway and payment

    @Test
    @DisplayName("BE05-01 an ONLINE Order starts with one AWAITING attempt: backend reference, whole total, stock deducted")
    void be05_01_attemptAtCheckout() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 2);

        assertThat(data.orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(data.payments(order)).hasSize(1);
        Map<String, Object> attempt = data.payments(order).get(0);
        assertThat((String) attempt.get("transaction_code")).startsWith("MOCK-").doesNotContain("~");
        assertThat(attempt.get("status")).isEqualTo("PENDING");
        assertThat(attempt.get("paid_at")).isNull();
        assertThat(attempt.get("amount")).isEqualTo(jdbc.queryForObject(
                "select total_amount from dbo.orders where order_id = ?", java.math.BigDecimal.class, order));
        assertThat(data.quantityAt(SERUM, "OS-THUDUC")).isEqualTo(20);
    }

    @Test
    @DisplayName("BE05-02 verified SUCCESS before the deadline: CONFIRMED / PAID, server time, no stock change")
    void be05_02_successConfirms() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
        Map<Long, List<Integer>> stock = data.stockSnapshot();
        long movements = data.movements();
        int before = history(order);

        assertThat(simulate(order, SimulatedResult.SUCCESS)).isEqualTo(PaymentOutcome.CONFIRMED);

        assertThat(data.orderStatus(order)).isEqualTo("CONFIRMED");
        assertThat(data.paymentStatus(order)).isEqualTo("PAID");
        Map<String, Object> row = data.payments(order).get(0);
        assertThat(row.get("status")).isEqualTo("SUCCESS");
        assertThat(row.get("paid_at")).isNotNull();
        assertThat(data.stockSnapshot()).isEqualTo(stock);
        assertThat(data.movements()).isEqualTo(movements);
        assertThat(history(order)).isEqualTo(before + 1);
        assertThat(lastNote(order)).startsWith("[PAYMENT_CONFIRMED]").contains(code(order).substring(0, 10));
        assertThat(data.fulfillmentStatus(data.onlyFulfillment(order))).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("BE05-03 a duplicate SUCCESS callback has no effect: one SUCCESS, no new history")
    void be05_03_duplicateCallback() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);
        simulate(order, SimulatedResult.SUCCESS);
        int history = history(order);

        assertThat(simulate(order, SimulatedResult.SUCCESS)).isEqualTo(PaymentOutcome.ALREADY_PAID);
        assertThat(simulate(order, SimulatedResult.FAILED)).isEqualTo(PaymentOutcome.ALREADY_PAID);

        assertThat(data.successfulPayments(order)).isEqualTo(1);
        assertThat(history(order)).isEqualTo(history);
        assertThat(data.orderStatus(order)).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("BE05-04 verified FAILED is terminal: Order CANCELLED, stock restored once, no retry on the same Order")
    void be05_04_failedIsTerminal() {
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        long attempt = data.attemptId(order);
        assertThat(data.quantityAt(SERUM, "OS-THUDUC")).isEqualTo(19);
        assertThat(data.quantityAt(SHAMPOO, "OS-GOVAP")).isEqualTo(19);

        assertThat(simulate(order, SimulatedResult.FAILED)).isEqualTo(PaymentOutcome.CANCELLED_PAYMENT_FAILED);

        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.paymentStatus(order)).isEqualTo("FAILED");
        assertThat(data.payment(attempt).get("status")).isEqualTo("FAILED");
        assertThat(data.fulfillments(order)).allMatch(f -> f.status().equals("CANCELLED"));
        assertThat(data.quantityAt(SERUM, "OS-THUDUC")).isEqualTo(20);
        assertThat(data.quantityAt(SHAMPOO, "OS-GOVAP")).isEqualTo(20);
        assertThat(data.cancelRestores(order)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from dbo.inventory_movements where reference_order_id = ? "
                + "and type = 'CANCEL_ORDER' and quantity_change > 0", Integer.class, order)).isEqualTo(2);
        // no retry on the same Order
        assertThatThrownBy(() -> paymentService.createOnlinePaymentAttempt(ALICE, order))
                .isInstanceOf(BadRequestException.class);
        assertThat(simulate(order, SimulatedResult.FAILED)).isEqualTo(PaymentOutcome.ALREADY_CANCELLED);
        assertThat(data.cancelRestores(order)).isEqualTo(2);
    }

    @Test
    @DisplayName("BE05-05 UNKNOWN before the deadline: recorded durably as in doubt, nothing else changes")
    void be05_05_unknownIsRecorded() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
        Map<Long, List<Integer>> stock = data.stockSnapshot();

        assertThat(simulate(order, SimulatedResult.UNKNOWN)).isEqualTo(PaymentOutcome.IN_DOUBT_RECORDED);

        Map<String, Object> row = data.payments(order).get(0);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat((String) row.get("transaction_code")).endsWith("~UNKNOWN");
        assertThat(row.get("paid_at")).isNotNull(); // the submission time
        assertThat(data.orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(data.paymentStatus(order)).isEqualTo("UNPAID");
        assertThat(data.stockSnapshot()).isEqualTo(stock);
        // the customer sees the reference without the internal marker
        assertThat(paymentService.getOrderPayments(ALICE, order).attempts().get(0).transactionCode())
                .doesNotContain("~");
    }

    @Test
    @DisplayName("BE05-06 a browser can never set a payment result: the old routes refuse and change nothing")
    void be05_06_browserResultRefused() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);
        long attempt = data.attemptId(order);

        assertThatThrownBy(() -> paymentService.markOnlinePaymentSuccess(ALICE, order, attempt))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> paymentService.markOnlinePaymentFailed(ALICE, order, attempt))
                .isInstanceOf(BadRequestException.class);

        assertThat(data.orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(data.payment(attempt).get("status")).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("BE05-07 another customer cannot drive the attempt; an attempt of another Order is not found")
    void be05_07_ownership() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);
        long other = data.onlinePickup("OS-THUDUC", SHAMPOO, 1);

        assertThatThrownBy(() -> reconciliation.simulate(BOB, order, data.attemptId(order), SimulatedResult.SUCCESS))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> reconciliation.simulate(ALICE, order, data.attemptId(other), SimulatedResult.SUCCESS))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(data.orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(data.payment(data.attemptId(order)).get("paid_at")).isNull();
    }

    @Test
    @DisplayName("BE05-08 COD and PAY_AT_STORE are not payment cases: no simulation, no timeout")
    void be05_08_offlineMethodsAreNotPaymentCases() {
        long cod = data.codDelivery("OS-THUDUC", SERUM, 1);
        long atStore = data.payAtStorePickup("OS-THUDUC", SHAMPOO, 1);
        data.ageOrder(cod, 60);
        data.ageOrder(atStore, 60);

        timeouts.scan();

        assertThat(data.orderStatus(cod)).isEqualTo("CONFIRMED");
        assertThat(data.orderStatus(atStore)).isEqualTo("CONFIRMED");
        assertThat(reconciliation.reconcile(cod, PaymentReconciliationService.Trigger.TIMEOUT_SCAN))
                .isEqualTo(PaymentOutcome.NOT_APPLICABLE);
    }

    // ================================================================== B. timeout and reconciliation

    @Test
    @DisplayName("BE05-09 expired and nothing collected: the scan cancels, restores once, closes the attempt")
    void be05_09_timeoutCancels() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
        data.ageOrder(order, 16);

        PaymentTimeoutService.ScanResult scan = timeouts.scan();

        assertThat(scan.changed()).isGreaterThanOrEqualTo(1);
        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.paymentStatus(order)).isEqualTo("FAILED");
        assertThat(data.payment(data.attemptId(order)).get("status")).isEqualTo("FAILED");
        assertThat(data.quantityAt(SERUM, "OS-THUDUC")).isEqualTo(22);
        assertThat(data.cancelRestores(order)).isEqualTo(1);
        assertThat(lastNote(order)).startsWith("[PAYMENT_TIMEOUT]");
        assertThat(data.fulfillmentStatus(data.onlyFulfillment(order))).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("BE05-10 not yet expired: the scan changes nothing")
    void be05_10_notExpired() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);
        data.ageOrder(order, 10);
        Map<Long, List<Integer>> stock = data.stockSnapshot();

        timeouts.scan();

        assertThat(data.orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(data.stockSnapshot()).isEqualTo(stock);
    }

    @Test
    @DisplayName("BE05-11 scanning again does not restore twice")
    void be05_11_scanIsIdempotent() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
        data.ageOrder(order, 16);

        timeouts.scan();
        int history = history(order);
        timeouts.scan();
        timeouts.scan();

        assertThat(data.cancelRestores(order)).isEqualTo(1);
        assertThat(data.quantityAt(SERUM, "OS-THUDUC")).isEqualTo(22);
        assertThat(history(order)).isEqualTo(history);
    }

    @Test
    @DisplayName("BE05-12 recovery: a collection before the deadline that was never applied confirms the Order late")
    void be05_12_unappliedCollectionRecovered() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
        data.ageOrder(order, 20);
        LocalDateTime collected = LocalDateTime.now().minusMinutes(18).withNano(0); // 2 minutes after creation
        captureAt(order, collected);
        Map<Long, List<Integer>> stock = data.stockSnapshot();

        timeouts.scan();

        assertThat(data.orderStatus(order)).isEqualTo("CONFIRMED");
        assertThat(data.paymentStatus(order)).isEqualTo("PAID");
        assertThat(data.payments(order).get(0).get("status")).isEqualTo("SUCCESS");
        assertThat(data.payments(order).get(0).get("paid_at").toString())
                .startsWith(collected.toLocalDate().toString());
        assertThat(data.stockSnapshot()).isEqualTo(stock);
        assertThat(data.cancelRestores(order)).isZero();
    }

    @Test
    @DisplayName("BE05-13 a collection at or after the deadline is never confirmed: cancel, restore once, refund once")
    void be05_13_lateCollectionRefunded() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
        data.ageOrder(order, 20);
        captureAt(order, LocalDateTime.now().minusMinutes(3).withNano(0)); // 17 minutes after creation

        timeouts.scan();

        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.paymentStatus(order)).isEqualTo("REFUNDED");
        Map<String, Object> row = data.payments(order).get(0);
        assertThat(row.get("status")).isEqualTo("SUCCESS");
        assertThat(row.get("refund_status")).isEqualTo("SIMULATED_REFUNDED");
        assertThat(row.get("refund_amount")).isEqualTo(row.get("amount"));
        assertThat(data.cancelRestores(order)).isEqualTo(1);
        assertThat(data.quantityAt(SERUM, "OS-THUDUC")).isEqualTo(22);
        timeouts.scan();
        assertThat(data.cancelRestores(order)).isEqualTo(1);
        assertThat(data.payments(order)).hasSize(1);
    }

    @Test
    @DisplayName("BE05-14 UNKNOWN inside the 15-minute window: reconciling, nothing is cancelled or restored")
    void be05_14_reconcilingInsideWindow() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
        simulate(order, SimulatedResult.UNKNOWN);
        data.ageOrder(order, 20);
        Map<Long, List<Integer>> stock = data.stockSnapshot();
        int history = history(order);

        timeouts.scan();

        assertThat(data.orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(data.stockSnapshot()).isEqualTo(stock);
        assertThat(history(order)).isEqualTo(history);
        assertThat(paymentService.getOrderPayments(ALICE, order).reconciling()).isTrue();
    }

    @Test
    @DisplayName("BE05-15 during reconciliation: no new payment, no customer cancel, no Staff PREPARING")
    void be05_15_reconcilingBlocksEverything() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
        simulate(order, SimulatedResult.UNKNOWN);
        data.ageOrder(order, 20);
        timeouts.scan();
        long fulfillment = data.onlyFulfillment(order);

        assertThat(conflict(PaymentConflictException.class, () -> cancel(order)).getCode())
                .isEqualTo(PaymentConflictException.PAYMENT_RECONCILING);
        assertThat(conflict(PaymentConflictException.class, () -> simulate(order, SimulatedResult.SUCCESS)).getCode())
                .isEqualTo(PaymentConflictException.PAYMENT_RECONCILING);
        assertThat(conflict(FulfillmentConflictException.class, () -> data.asStaff(STAFF_THUDUC,
                () -> staff.perform(fulfillment, FulfillmentAction.PREPARE, null))).getCode())
                .isEqualTo(FulfillmentConflictException.PAYMENT_PENDING);
        assertThatThrownBy(() -> paymentService.createOnlinePaymentAttempt(ALICE, order))
                .isInstanceOf(BadRequestException.class);

        assertThat(data.orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(data.cancelRestores(order)).isZero();
    }

    @Test
    @DisplayName("BE05-16 still UNKNOWN after 30 minutes: NEEDS_ATTENTION, stock stays deducted, one history row")
    void be05_16_needsAttentionAfterWindow() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
        simulate(order, SimulatedResult.UNKNOWN);
        data.ageOrder(order, 31);
        Map<Long, List<Integer>> stock = data.stockSnapshot();

        timeouts.scan();

        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION");
        assertThat(data.paymentStatus(order)).isEqualTo("UNPAID");
        assertThat(data.stockSnapshot()).isEqualTo(stock);
        assertThat(data.cancelRestores(order)).isZero();
        assertThat(lastNote(order)).startsWith("[RECONCILIATION_TIMEOUT]").contains("ref=MOCK-");
        int history = history(order);
        timeouts.scan();
        assertThat(history(order)).isEqualTo(history);
        assertThat(conflict(FulfillmentConflictException.class, () -> data.asStaff(STAFF_THUDUC,
                () -> staff.perform(data.onlyFulfillment(order), FulfillmentAction.PREPARE, null))).getCode())
                .isEqualTo(FulfillmentConflictException.ORDER_NEEDS_ATTENTION);
        assertThat(conflict(FulfillmentConflictException.class, () -> cancel(order)).getCode())
                .isEqualTo(FulfillmentConflictException.ORDER_NEEDS_ATTENTION);
    }

    @Test
    @DisplayName("BE05-17 UNKNOWN then a verified collection inside the window confirms on the next scan")
    void be05_17_unknownThenSuccess() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
        simulate(order, SimulatedResult.UNKNOWN);
        data.ageOrder(order, 20);
        timeouts.scan();
        assertThat(data.orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        // the gateway answers: collected at the original submission time. Move that time to before the deadline.
        captureAt(order, LocalDateTime.now().minusMinutes(18).withNano(0));

        asAdmin(() -> adminPayments.settleGateway(order, true));

        assertThat(data.orderStatus(order)).isEqualTo("CONFIRMED");
        assertThat(data.paymentStatus(order)).isEqualTo("PAID");
        assertThat(data.cancelRestores(order)).isZero();
    }

    @Test
    @DisplayName("BE05-18 UNKNOWN then a verified decline inside the window cancels and restores once")
    void be05_18_unknownThenFailed() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
        simulate(order, SimulatedResult.UNKNOWN);
        data.ageOrder(order, 20);

        PaymentOutcome outcome = asAdmin(() -> adminPayments.settleGateway(order, false));

        assertThat(outcome).isEqualTo(PaymentOutcome.CANCELLED_PAYMENT_FAILED);
        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.cancelRestores(order)).isEqualTo(1);
        assertThat(data.quantityAt(SERUM, "OS-THUDUC")).isEqualTo(22);
    }

    @Test
    @DisplayName("BE05-19 NEEDS_ATTENTION: Admin re-check with no new evidence changes nothing but is audited")
    void be05_19_recheckStillUnknown() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
        simulate(order, SimulatedResult.UNKNOWN);
        data.ageOrder(order, 31);
        timeouts.scan();
        Map<Long, List<Integer>> stock = data.stockSnapshot();
        int history = history(order);

        PaymentOutcome outcome = asAdmin(() -> adminPayments.recheck(order));

        assertThat(outcome).isEqualTo(PaymentOutcome.NEEDS_ATTENTION);
        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION");
        assertThat(data.stockSnapshot()).isEqualTo(stock);
        assertThat(history(order)).isEqualTo(history + 1);
        assertThat(lastNote(order)).startsWith("[ADMIN_RECHECK]");
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_status_history h join dbo.users u on "
                + "u.user_id = h.changed_by_user_id where h.order_id = ? and u.email = ?", Integer.class, order,
                ADMIN)).isEqualTo(1);
    }

    @Test
    @DisplayName("BE05-20 NEEDS_ATTENTION and the gateway says collected in time: Admin confirms, no second deduction")
    void be05_20_adminConfirms() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
        simulate(order, SimulatedResult.UNKNOWN);
        data.ageOrder(order, 31);
        timeouts.scan();
        captureAt(order, LocalDateTime.now().minusMinutes(29).withNano(0)); // 2 minutes after creation
        Map<Long, List<Integer>> stock = data.stockSnapshot();
        long movements = data.movements();

        PaymentOutcome outcome = asAdmin(() -> adminPayments.settleGateway(order, true));

        assertThat(outcome).isEqualTo(PaymentOutcome.CONFIRMED);
        assertThat(data.orderStatus(order)).isEqualTo("CONFIRMED");
        assertThat(data.paymentStatus(order)).isEqualTo("PAID");
        assertThat(data.stockSnapshot()).isEqualTo(stock);
        assertThat(data.movements()).isEqualTo(movements);
        assertThat(data.fulfillmentStatus(data.onlyFulfillment(order))).isEqualTo("CONFIRMED");
        // the Order is workable again
        data.asStaff(STAFF_THUDUC, () -> staff.perform(data.onlyFulfillment(order), FulfillmentAction.PREPARE, null));
        assertThat(data.fulfillmentStatus(data.onlyFulfillment(order))).isEqualTo("PREPARING");
    }

    @Test
    @DisplayName("BE05-21 NEEDS_ATTENTION and the gateway says not collected: Admin cancels and restores once")
    void be05_21_adminCancels() {
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        simulate(order, SimulatedResult.UNKNOWN);
        data.ageOrder(order, 31);
        timeouts.scan();

        PaymentOutcome outcome = asAdmin(() -> adminPayments.settleGateway(order, false));
        PaymentOutcome again = asAdmin(() -> adminPayments.recheck(order));

        assertThat(outcome).isEqualTo(PaymentOutcome.CANCELLED_PAYMENT_FAILED);
        assertThat(again).isEqualTo(PaymentOutcome.ALREADY_CANCELLED);
        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.cancelRestores(order)).isEqualTo(2);
        assertThat(data.quantityAt(SERUM, "OS-THUDUC")).isEqualTo(20);
        assertThat(data.quantityAt(SHAMPOO, "OS-GOVAP")).isEqualTo(20);
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_fulfillment_status_history h join dbo.users u "
                + "on u.user_id = h.changed_by_user_id where u.email = ? and h.fulfillment_id in (select "
                + "fulfillment_id from dbo.order_fulfillments where order_id = ?)", Integer.class, ADMIN, order))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("BE05-22 NEEDS_ATTENTION and collected after the deadline: Admin cancels, restores once, refunds once")
    void be05_22_adminLateCollection() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
        simulate(order, SimulatedResult.UNKNOWN);
        data.ageOrder(order, 31);
        timeouts.scan();
        captureAt(order, LocalDateTime.now().minusMinutes(10).withNano(0)); // 21 minutes after creation

        PaymentOutcome outcome = asAdmin(() -> adminPayments.settleGateway(order, true));
        asAdmin(() -> adminPayments.recheck(order));

        assertThat(outcome).isEqualTo(PaymentOutcome.CANCELLED_LATE_PAYMENT);
        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.paymentStatus(order)).isEqualTo("REFUNDED");
        assertThat(data.payments(order)).hasSize(1);
        assertThat(data.payments(order).get(0).get("refund_status")).isEqualTo("SIMULATED_REFUNDED");
        assertThat(data.cancelRestores(order)).isEqualTo(1);
    }

    @Test
    @DisplayName("BE05-23 a collection that arrives after the Order was cancelled is recorded and refunded once")
    void be05_23_lateCallbackOnCancelled() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
        data.ageOrder(order, 16);
        timeouts.scan();
        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        Map<Long, List<Integer>> stock = data.stockSnapshot();

        assertThat(simulate(order, SimulatedResult.SUCCESS)).isEqualTo(PaymentOutcome.LATE_PAYMENT_REFUNDED);
        assertThat(simulate(order, SimulatedResult.SUCCESS)).isEqualTo(PaymentOutcome.LATE_PAYMENT_REFUNDED);

        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED"); // never revived
        assertThat(data.paymentStatus(order)).isEqualTo("REFUNDED");
        assertThat(data.stockSnapshot()).isEqualTo(stock); // no second restore, no re-deduction
        assertThat(data.cancelRestores(order)).isEqualTo(1);
        assertThat(data.successfulPayments(order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from dbo.payments where order_id = ? and refund_status = "
                + "'SIMULATED_REFUNDED'", Integer.class, order)).isEqualTo(1);
        assertThat(simulate(order, SimulatedResult.FAILED)).isEqualTo(PaymentOutcome.ALREADY_CANCELLED);
    }

    @Test
    @DisplayName("BE05-24 SUCCESS at the moment the deadline has passed is not a normal confirmation")
    void be05_24_boundary() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);
        data.ageOrder(order, 15); // deadline reached (now >= created_at + 15 min)

        assertThat(simulate(order, SimulatedResult.SUCCESS)).isEqualTo(PaymentOutcome.CANCELLED_LATE_PAYMENT);

        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.paymentStatus(order)).isEqualTo("REFUNDED");
    }

    @Test
    @DisplayName("BE05-25 a gateway that cannot answer is UNKNOWN, never FAILED: nothing is cancelled")
    void be05_25_gatewayOutageIsUnknown() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);
        data.ageOrder(order, 16);
        doThrow(new IllegalStateException("gateway timeout")).when(gateway).verify(any(), any());
        Map<Long, List<Integer>> stock = data.stockSnapshot();

        timeouts.scan();

        assertThat(data.orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(data.stockSnapshot()).isEqualTo(stock);
        assertThat(data.cancelRestores(order)).isZero();
    }

    // ================================================================== C. cancellation and restoration

    @Test
    @DisplayName("BE05-26 customer cancel of an unpaid ONLINE Order: cancelled, restored once, payment stays UNPAID")
    void be05_26_cancelUnpaid() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 2);

        cancel(order);
        cancel(order); // repeated request: no side effect

        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.paymentStatus(order)).isEqualTo("UNPAID");
        assertThat(data.payment(data.attemptId(order)).get("status")).isEqualTo("FAILED");
        assertThat(data.quantityAt(SERUM, "OS-THUDUC")).isEqualTo(22);
        assertThat(data.cancelRestores(order)).isEqualTo(1);
        assertThat(data.fulfillmentStatus(data.onlyFulfillment(order))).isEqualTo("CANCELLED");
        assertThat(lastNote(order)).isEqualTo("Khách hàng hủy đơn trước khi chuẩn bị hàng");
    }

    @Test
    @DisplayName("BE05-27 customer cancel of a PAID ONLINE Order before PREPARING: restored once and refunded once")
    void be05_27_cancelPaidRefunds() {
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        simulate(order, SimulatedResult.SUCCESS);

        cancel(order);
        cancel(order);

        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.paymentStatus(order)).isEqualTo("REFUNDED");
        Map<String, Object> row = data.payments(order).get(0);
        assertThat(row.get("status")).isEqualTo("SUCCESS"); // evidence kept
        assertThat(row.get("refund_status")).isEqualTo("SIMULATED_REFUNDED");
        assertThat(row.get("refund_amount")).isEqualTo(row.get("amount"));
        assertThat(row.get("refunded_at")).isNotNull();
        assertThat(data.cancelRestores(order)).isEqualTo(2);
        assertThat(data.quantityAt(SERUM, "OS-THUDUC")).isEqualTo(20);
        assertThat(data.quantityAt(SHAMPOO, "OS-GOVAP")).isEqualTo(20);
    }

    @Test
    @DisplayName("BE05-28 cancel after a Store started PREPARING is refused: no stock, money or status change")
    void be05_28_cancelAfterPreparing() {
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        simulate(order, SimulatedResult.SUCCESS);
        data.asStaff(STAFF_THUDUC, () -> staff.perform(data.fulfillmentOf(order, "OS-THUDUC"),
                FulfillmentAction.PREPARE, null));
        Map<Long, List<Integer>> stock = data.stockSnapshot();

        FulfillmentConflictException ex = conflict(FulfillmentConflictException.class, () -> cancel(order));

        assertThat(ex.getCode()).isEqualTo(FulfillmentConflictException.ORDER_ALREADY_PREPARING);
        assertThat(data.orderStatus(order)).isEqualTo("PROCESSING");
        assertThat(data.paymentStatus(order)).isEqualTo("PAID");
        assertThat(data.payments(order).get(0).get("refund_status")).isEqualTo("NONE");
        assertThat(data.stockSnapshot()).isEqualTo(stock);
        assertThat(data.cancelRestores(order)).isZero();
    }

    @Test
    @DisplayName("BE05-29 a SKU split between two Stores is restored per Store, each its own part")
    void be05_29_splitSameSkuRestoredPerStore() {
        long order = data.onlineSplitSameSku(SERUM, "OS-THUDUC", 2, "OS-GOVAP", 2, 3);
        int thuDuc = data.quantityAt(SERUM, "OS-THUDUC");
        int goVap = data.quantityAt(SERUM, "OS-GOVAP");
        assertThat(thuDuc + goVap).isEqualTo(4 - 3);
        assertThat(data.fulfillments(order)).hasSize(2);
        List<Map<String, Object>> items = jdbc.queryForList("select oi.quantity, sp.store_id from dbo.order_items oi "
                + "join dbo.store_products sp on sp.store_product_id = oi.store_product_id where oi.order_id = ?", order);
        assertThat(items).hasSize(2);

        simulate(order, SimulatedResult.FAILED);

        assertThat(data.quantityAt(SERUM, "OS-THUDUC")).isEqualTo(2);
        assertThat(data.quantityAt(SERUM, "OS-GOVAP")).isEqualTo(2);
        assertThat(data.cancelRestores(order)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select sum(quantity_change) from dbo.inventory_movements where "
                + "reference_order_id = ? and type = 'CANCEL_ORDER'", Integer.class, order)).isEqualTo(3);
    }

    @Test
    @DisplayName("BE05-30 COD and PAY_AT_STORE cancel keep their behaviour: restored, UNPAID, no refund row")
    void be05_30_offlineCancel() {
        long cod = data.codDelivery("OS-THUDUC", SERUM, 2);
        long atStore = data.payAtStorePickup("OS-THUDUC", SHAMPOO, 1);

        cancel(cod);
        cancel(atStore);

        for (long order : new long[]{cod, atStore}) {
            assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
            assertThat(data.paymentStatus(order)).isEqualTo("UNPAID");
            assertThat(data.cancelRestores(order)).isEqualTo(1);
            assertThat(data.payments(order)).isEmpty();
        }
    }

    @Test
    @DisplayName("BE05-31 a part that FAILED after PREPARING is never restocked by a payment or cancel path")
    void be05_31_failedPartIsNotRestocked() {
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        simulate(order, SimulatedResult.SUCCESS);
        long part = data.fulfillmentOf(order, "OS-THUDUC");
        for (FulfillmentAction step : List.of(FulfillmentAction.PREPARE, FulfillmentAction.PACK,
                FulfillmentAction.SHIP)) {
            data.asStaff(STAFF_THUDUC, () -> staff.perform(part, step, null));
        }
        data.asStaff(STAFF_THUDUC, () -> staff.perform(part, FulfillmentAction.FAIL_DELIVERY, "Khách không nhận hàng"));
        Map<Long, List<Integer>> stock = data.stockSnapshot();

        assertThat(asAdmin(() -> adminPayments.recheck(order))).isIn(PaymentOutcome.ALREADY_PAID,
                PaymentOutcome.NOT_APPLICABLE);
        assertThat(conflict(FulfillmentConflictException.class, () -> cancel(order)).getCode())
                .isEqualTo(FulfillmentConflictException.ORDER_NEEDS_ATTENTION);
        timeouts.scan();

        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION");
        assertThat(data.paymentStatus(order)).isEqualTo("PAID");
        assertThat(data.stockSnapshot()).isEqualTo(stock);
        assertThat(data.cancelRestores(order)).isZero();
    }

    // ================================================================== D. simulated refund

    @Test
    @DisplayName("BE05-32 a failing refund simulation keeps the cancel, leaves SIMULATION_FAILED, and retry is safe")
    void be05_32_refundFailureAndRetry() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
        simulate(order, SimulatedResult.SUCCESS);
        doThrow(new IllegalStateException("simulated bank outage")).when(refundSimulator).simulate(any());

        cancel(order);

        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.paymentStatus(order)).isEqualTo("PAID"); // money not given back yet
        assertThat(data.payments(order).get(0).get("refund_status")).isEqualTo("SIMULATION_FAILED");
        assertThat(data.cancelRestores(order)).isEqualTo(1);
        assertThat(data.quantityAt(SERUM, "OS-THUDUC")).isEqualTo(22);

        org.mockito.Mockito.reset(refundSimulator);
        RefundService.Result retry = asAdmin(() -> adminPayments.retryRefund(order));
        RefundService.Result again = asAdmin(() -> adminPayments.retryRefund(order));

        assertThat(retry).isEqualTo(RefundService.Result.REFUNDED);
        assertThat(again).isEqualTo(RefundService.Result.ALREADY_REFUNDED);
        assertThat(data.paymentStatus(order)).isEqualTo("REFUNDED");
        assertThat(data.payments(order).get(0).get("refund_status")).isEqualTo("SIMULATED_REFUNDED");
        assertThat(data.cancelRestores(order)).isEqualTo(1); // stock restoration is not repeated
    }

    @Test
    @DisplayName("BE05-33 nothing is refunded unless money was collected; an unpaid cancel creates no refund")
    void be05_33_noRefundWithoutCollection() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);
        cancel(order);

        assertThat(data.payments(order).get(0).get("refund_status")).isEqualTo("NONE");
        assertThat(conflict(PaymentConflictException.class, () -> asAdmin(() -> adminPayments.retryRefund(order)))
                .getCode()).isEqualTo(PaymentConflictException.NOTHING_TO_REFUND);
    }

    // ================================================================== E. Admin authorization and bounds

    @Test
    @DisplayName("BE05-34 only an administrator can use the payment console")
    void be05_34_adminOnly() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);

        for (String role : List.of("ROLE_CUSTOMER", "ROLE_STAFF")) {
            String email = role.equals("ROLE_STAFF") ? STAFF_THUDUC : ALICE;
            assertThatThrownBy(() -> data.asRole(email, role, () -> adminPayments.getCases(0)))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> data.asRole(email, role, () -> adminPayments.getCase(order)))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> data.asRole(email, role, () -> adminPayments.recheck(order)))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> data.asRole(email, role, () -> adminPayments.settleGateway(order, true)))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> data.asRole(email, role, () -> adminPayments.retryRefund(order)))
                    .isInstanceOf(AccessDeniedException.class);
        }
        assertThat(data.orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(data.payment(data.attemptId(order)).get("paid_at")).isNull();
    }

    @Test
    @DisplayName("BE05-35 an administrator cannot mark PAID without ledger evidence or invent a collection")
    void be05_35_noEvidenceNoPaid() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);

        assertThat(conflict(PaymentConflictException.class, () -> asAdmin(() -> adminPayments.settleGateway(order, true)))
                .getCode()).isEqualTo(PaymentConflictException.NOT_A_PAYMENT_CASE);
        PaymentOutcome outcome = asAdmin(() -> adminPayments.recheck(order));

        assertThat(outcome).isEqualTo(PaymentOutcome.AWAITING);
        assertThat(data.orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(data.paymentStatus(order)).isEqualTo("UNPAID");
        assertThat(data.successfulPayments(order)).isZero();
    }

    @Test
    @DisplayName("BE05-36 an administrator cannot revive a cancelled Order or touch a COD Order")
    void be05_36_noRevive() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);
        cancel(order);
        long cod = data.codDelivery("OS-THUDUC", SHAMPOO, 1);

        assertThat(asAdmin(() -> adminPayments.recheck(order))).isEqualTo(PaymentOutcome.ALREADY_CANCELLED);
        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(conflict(PaymentConflictException.class, () -> asAdmin(() -> adminPayments.recheck(cod))).getCode())
                .isEqualTo(PaymentConflictException.NOT_A_PAYMENT_CASE);
    }

    @Test
    @DisplayName("BE05-37 the Admin list shows NEEDS_ATTENTION and failed refunds; the case detail has the evidence")
    void be05_37_adminList() {
        long unknown = data.onlinePickup("OS-THUDUC", SERUM, 1);
        simulate(unknown, SimulatedResult.UNKNOWN);
        data.ageOrder(unknown, 31);
        timeouts.scan();

        var cases = asAdmin(() -> adminPayments.getCases(0));
        var detail = asAdmin(() -> adminPayments.getCase(unknown));

        assertThat(cases.getContent()).extracting(c -> c.orderId()).contains(unknown);
        assertThat(detail.attempts()).hasSize(1);
        assertThat(detail.attempts().get(0).ledgerState().name()).isEqualTo("IN_DOUBT");
        assertThat(detail.canRecheck()).isTrue();
        assertThat(detail.canSettleGateway()).isTrue();
        assertThat(detail.canRetryRefund()).isFalse();
        assertThat(detail.detail().history()).isNotEmpty();
    }
}
