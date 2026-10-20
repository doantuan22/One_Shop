package com.oneshop;

import com.oneshop.dto.response.OrderRefundSummaryResponse;
import com.oneshop.entity.Payment;
import com.oneshop.exception.FulfillmentConflictException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.AdminOperationsService;
import com.oneshop.service.CartService;
import com.oneshop.service.CheckoutService;
import com.oneshop.service.CustomerOrderService;
import com.oneshop.service.fulfillment.AdminFulfillmentExceptionService;
import com.oneshop.service.fulfillment.AdminFulfillmentExceptionService.GoodsStatus;
import com.oneshop.service.fulfillment.AdminFulfillmentExceptionService.Outcome;
import com.oneshop.service.fulfillment.FulfillmentAction;
import com.oneshop.service.fulfillment.StaffFulfillmentService;
import com.oneshop.service.payment.AdminPaymentService;
import com.oneshop.service.payment.OrderRefundViewService;
import com.oneshop.service.payment.PartialRefundService;
import com.oneshop.service.payment.PaymentCaseCategory;
import com.oneshop.service.payment.PaymentReconciliationService;
import com.oneshop.service.payment.PaymentReconciliationService.SimulatedResult;
import com.oneshop.service.payment.RefundSimulator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * OPEN-10.1 on the real SQL Server: the simulated refund ledger ({@code payment_refunds}), the partial refund rule (the
 * goods of an undeliverable part; the one shipping fee only when nothing is delivered), the "unconfirmed goods" wait,
 * several FAILED parts, the customer / admin views and the guarantees against refunding twice. These tests commit;
 * {@link SeedGuardV3} puts the test database back, so they only run on a {@code *_test} database.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=test-only-secret-test-only-secret-1234567890",
        "oneshop.checkout.online-enabled=true",
        "oneshop.payment.timeout-worker-enabled=false"})
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class PartialRefundDatabaseIntegrationTest {

    static final String ADMIN = "admin@oneshop.vn";
    static final String ALICE = "khachhang1@example.com";
    static final String BOB = "khachhang2@example.com";
    static final String STAFF_THUDUC = "staff.thuduc@oneshop.vn";
    static final String STAFF_GOVAP = "staff.govap@oneshop.vn";
    static final String SERUM = "SIMPLE-PURIFY-150";
    static final String SHAMPOO = "VASELINE-LIP-ORIG-48";

    @Autowired
    AdminFulfillmentExceptionService exceptions;
    @Autowired
    StaffFulfillmentService staff;
    @Autowired
    AdminOperationsService operations;
    @Autowired
    AdminPaymentService adminPayments;
    @Autowired
    OrderRefundViewService refundView;
    @Autowired
    CustomerOrderService customerOrders;
    @Autowired
    PaymentReconciliationService reconciliation;
    @MockitoSpyBean
    RefundSimulator refundSimulator;
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
        reset(refundSimulator);
        if (remembered) {
            guard.restore();
        }
    }

    // ------------------------------------------------------------------ helpers

    private <T> T asAdmin(Supplier<T> action) {
        return data.asRole(ADMIN, "ROLE_ADMIN", action);
    }

    private void act(String staffEmail, long fulfillment, FulfillmentAction action, String input) {
        data.asStaff(staffEmail, () -> staff.perform(fulfillment, action, input));
    }

    private void act(String staffEmail, long fulfillment, FulfillmentAction action) {
        act(staffEmail, fulfillment, action, null);
    }

    private void toShipping(String staffEmail, long part) {
        act(staffEmail, part, FulfillmentAction.PREPARE);
        act(staffEmail, part, FulfillmentAction.PACK);
        act(staffEmail, part, FulfillmentAction.SHIP, "TRK-" + part);
    }

    private void failDelivery(String staffEmail, long part, String reason) {
        toShipping(staffEmail, part);
        act(staffEmail, part, FulfillmentAction.FAIL_DELIVERY, reason);
    }

    private void completeDelivery(String staffEmail, long part) {
        toShipping(staffEmail, part);
        act(staffEmail, part, FulfillmentAction.COMPLETE_DELIVERY);
    }

    /** Both parts handed to the carrier, the first one fails for real, the hold is lifted as a concurrent report found it, the second fails. */
    private void twoFailedParts(long order, long first, long second) {
        toShipping(STAFF_THUDUC, first);
        toShipping(STAFF_GOVAP, second);
        act(STAFF_THUDUC, first, FulfillmentAction.FAIL_DELIVERY, "Khách vắng nhà");
        jdbc.update("update dbo.orders set order_status = 'SHIPPING' where order_id = ?", order);
        act(STAFF_GOVAP, second, FulfillmentAction.FAIL_DELIVERY, "Khách vắng nhà");
    }

    private long paidTwoStore() {
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        data.markPaid(order);
        jdbc.update("update dbo.payments set refund_status = 'NONE' where order_id = ?", order);
        return order;
    }

    private Outcome undeliverable(long order, long part, GoodsStatus goods, String reason) {
        return asAdmin(() -> exceptions.markUndeliverable(order, part, goods, reason));
    }

    private Outcome redeliver(long order, long part, String reason) {
        return asAdmin(() -> exceptions.redeliver(order, part, true, reason));
    }

    private BigDecimal money(String sql, Object... args) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal collected(long order) {
        return money("select coalesce(sum(amount), 0) from dbo.payments where order_id = ? and status = 'SUCCESS'", order);
    }

    private BigDecimal refunded(long order) {
        return money("select coalesce(sum(amount), 0) from dbo.payment_refunds where order_id = ? and status = 'SIMULATED_REFUNDED'", order);
    }

    private BigDecimal goodsOf(long part) {
        return money("select coalesce(sum(subtotal), 0) from dbo.order_items where fulfillment_id = ?", part);
    }

    private BigDecimal fee(long order) {
        return money("select shipping_fee from dbo.orders where order_id = ?", order);
    }

    private int ledgerRows(long order) {
        return jdbc.queryForObject("select count(*) from dbo.payment_refunds where order_id = ?", Integer.class, order);
    }

    private long counts(String table) {
        return jdbc.queryForObject("select count(*) from dbo." + table, Long.class);
    }

    private int qty(long part) {
        return jdbc.queryForObject("select top 1 sp.quantity from dbo.order_items oi join dbo.store_products sp on "
                + "sp.store_product_id = oi.store_product_id where oi.fulfillment_id = ? order by oi.order_item_id", Integer.class, part);
    }

    private PaymentCaseCategory category(long order) {
        return asAdmin(() -> adminPayments.getCases(null, String.valueOf(order), 0)).getContent().stream()
                .filter(c -> c.orderId() == order).findFirst().orElseThrow().category();
    }

    // ================================================================== amounts

    @Test
    @DisplayName("OPEN101-01 some goods delivered: only the goods of the undeliverable part are refunded; the shipping fee is kept; the ledger has who, why, how much, when")
    void partialRefundGoodsOnly() {
        long order = paidTwoStore();
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        long goVap = data.fulfillmentOf(order, "OS-GOVAP");
        completeDelivery(STAFF_GOVAP, goVap);
        failDelivery(STAFF_THUDUC, thuDuc, "Xe hỏng giữa đường");
        long payments = counts("payments");
        long orders = counts("orders");
        BigDecimal collected = collected(order);

        Outcome outcome = undeliverable(order, thuDuc, GoodsStatus.RETURNED, "Khách không còn nhu cầu, đã xác nhận qua điện thoại");

        assertThat(outcome.refund().goods()).isEqualByComparingTo(goodsOf(thuDuc));
        assertThat(outcome.refund().shipping()).isEqualByComparingTo("0");
        assertThat(refunded(order)).isEqualByComparingTo(goodsOf(thuDuc));
        assertThat(collected.subtract(refunded(order))).as("kept = goods delivered + the one shipping fee")
                .isEqualByComparingTo(goodsOf(goVap).add(fee(order)));
        assertThat(fee(order)).isEqualByComparingTo("30000");
        var row = jdbc.queryForMap("select * from dbo.payment_refunds where order_id = ?", order);
        assertThat(row.get("component")).isEqualTo("GOODS");
        assertThat(((Number) row.get("fulfillment_id")).longValue()).isEqualTo(thuDuc);
        assertThat(row.get("status")).isEqualTo("SIMULATED_REFUNDED");
        assertThat((String) row.get("reason")).contains("Khách không còn nhu cầu");
        assertThat(((Number) row.get("requested_by_user_id")).longValue()).isEqualTo(
                jdbc.queryForObject("select user_id from dbo.users where email = ?", Long.class, ADMIN));
        assertThat(row.get("requested_at")).isNotNull();
        assertThat(row.get("refunded_at")).isNotNull();
        assertThat(((Number) row.get("payment_id")).longValue()).isEqualTo(
                jdbc.queryForObject("select payment_id from dbo.payments where order_id = ? and status = 'SUCCESS'", Long.class, order));
        assertThat(counts("payments")).as("no new Payment").isEqualTo(payments);
        assertThat(counts("orders")).as("no new Order").isEqualTo(orders);
        assertThat(data.paymentStatus(order)).as("only part of the money went back").isEqualTo("PAID");
        assertThat(data.orderStatus(order)).isEqualTo("COMPLETED");
        assertThat(data.fulfillmentStatus(goVap)).as("the delivered part is untouched").isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("OPEN101-02 nothing delivered (pickup, no shipping fee): all the goods go back; Payment and Order become REFUNDED / CANCELLED")
    void wholeRefundWhenNothingDelivered() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);
        data.markPaid(order);
        jdbc.update("update dbo.payments set refund_status = 'NONE' where order_id = ?", order);
        long part = data.onlyFulfillment(order);
        act(STAFF_THUDUC, part, FulfillmentAction.PREPARE);
        act(STAFF_THUDUC, part, FulfillmentAction.MARK_READY);
        act(STAFF_THUDUC, part, FulfillmentAction.FAIL_PICKUP, "Khách không đến nhận");
        BigDecimal collected = collected(order);
        BigDecimal goods = goodsOf(part);

        Outcome outcome = undeliverable(order, part, GoodsStatus.RETURNED, "Khách hủy nhận");

        BigDecimal feeOfOrder = fee(order);
        assertThat(outcome.refund().fullyRefunded()).isTrue();
        assertThat(outcome.refund().goods()).isEqualByComparingTo(goods);
        assertThat(feeOfOrder).as("a pickup Order has no shipping fee").isEqualByComparingTo("0");
        assertThat(outcome.refund().shipping()).isEqualByComparingTo("0");
        assertThat(goods).isEqualByComparingTo(collected);
        assertThat(refunded(order)).isEqualByComparingTo(collected);
        assertThat(jdbc.queryForObject("select count(*) from dbo.payment_refunds where order_id = ? and component = 'GOODS'", Integer.class, order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from dbo.payment_refunds where order_id = ? and component = 'SHIPPING'", Integer.class, order)).isZero();
        assertThat(data.paymentStatus(order)).isEqualTo("REFUNDED");
        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select refund_status from dbo.payments where order_id = ? and status = 'SUCCESS'", String.class, order))
                .isEqualTo("SIMULATED_REFUNDED");
        assertThat(category(order)).isEqualTo(PaymentCaseCategory.REFUNDED);
    }

    @Test
    @DisplayName("OPEN101-03 two undeliverable parts, nothing delivered: goods of each part when decided, the shipping fee with the last one; total == collected")
    void shippingGoesBackOnlyWithTheLastPart() {
        long order = paidTwoStore();
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        long goVap = data.fulfillmentOf(order, "OS-GOVAP");
        twoFailedParts(order, thuDuc, goVap);

        Outcome first = undeliverable(order, thuDuc, GoodsStatus.RETURNED, "Khách vắng nhà, hủy phần này");
        assertThat(first.refund().shipping()).isEqualByComparingTo("0");
        assertThat(refunded(order)).isEqualByComparingTo(goodsOf(thuDuc));
        assertThat(data.orderStatus(order)).as("the hold stays until every failed part has a decision").isEqualTo("NEEDS_ATTENTION");

        Outcome last = undeliverable(order, goVap, GoodsStatus.RETURNED, "Khách vắng nhà, hủy phần này");
        assertThat(last.refund().shipping()).isEqualByComparingTo(fee(order));
        assertThat(refunded(order)).isEqualByComparingTo(collected(order));
        assertThat(jdbc.queryForObject("select count(*) from dbo.payment_refunds where order_id = ? and component = 'SHIPPING'", Integer.class, order)).isEqualTo(1);
        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.paymentStatus(order)).isEqualTo("REFUNDED");
        assertThat(ledgerRows(order)).isEqualTo(3);
    }

    @Test
    @DisplayName("OPEN101-04 one part redelivered and delivered, the other undeliverable: shipping fee kept, Order COMPLETED, refund = goods of the undeliverable part")
    void redeliverOneRefundTheOther() {
        long order = paidTwoStore();
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        long goVap = data.fulfillmentOf(order, "OS-GOVAP");
        twoFailedParts(order, thuDuc, goVap);

        redeliver(order, thuDuc, "Đã xác nhận lại địa chỉ với khách");
        assertThat(data.orderStatus(order)).as("one failed part still waits: the Order stays held").isEqualTo("NEEDS_ATTENTION");
        assertThat(ledgerRows(order)).isZero();

        Outcome outcome = undeliverable(order, goVap, GoodsStatus.LOST, "Vận chuyển xác nhận thất lạc");
        assertThat(outcome.refund().shipping()).as("the other part is being delivered: shipping kept").isEqualByComparingTo("0");
        assertThat(data.orderStatus(order)).as("all exceptions resolved: work resumes").isEqualTo("PROCESSING");

        act(STAFF_THUDUC, thuDuc, FulfillmentAction.SHIP, "TRK-NEW");
        act(STAFF_THUDUC, thuDuc, FulfillmentAction.COMPLETE_DELIVERY);
        assertThat(data.orderStatus(order)).isEqualTo("COMPLETED");
        assertThat(data.paymentStatus(order)).isEqualTo("PAID");
        assertThat(refunded(order)).isEqualByComparingTo(goodsOf(goVap));
        assertThat(collected(order).subtract(refunded(order))).isEqualByComparingTo(goodsOf(thuDuc).add(fee(order)));
        assertThat(refundView.forAdmin(order).closedWithUndelivered()).isTrue();
    }

    @Test
    @DisplayName("OPEN101-05 a refund can never exceed what was collected: nothing is written and the decision is rolled back")
    void neverMoreThanCollected() {
        long order = paidTwoStore();
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        failDelivery(STAFF_THUDUC, thuDuc, "Sai địa chỉ");
        jdbc.update("update dbo.payments set amount = 1 where order_id = ? and status = 'SUCCESS'", order);
        int stock = qty(thuDuc);
        long movements = counts("inventory_movements");

        assertThatThrownBy(() -> undeliverable(order, thuDuc, GoodsStatus.RETURNED, "Thử hoàn vượt số đã thu"))
                .isInstanceOf(FulfillmentConflictException.class);

        assertThat(ledgerRows(order)).isZero();
        assertThat(data.fulfillmentStatus(thuDuc)).as("the whole decision rolled back").isEqualTo("FAILED");
        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION");
        assertThat(qty(thuDuc)).isEqualTo(stock);
        assertThat(counts("inventory_movements")).isEqualTo(movements);
    }

    @Test
    @DisplayName("OPEN101-06 the database itself refuses a second GOODS / SHIPPING / WHOLE_ORDER refund and malformed rows")
    void databaseConstraints() {
        long order = paidTwoStore();
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        long payment = jdbc.queryForObject("select payment_id from dbo.payments where order_id = ? and status = 'SUCCESS'", Long.class, order);
        String insert = "insert into dbo.payment_refunds (payment_id, order_id, fulfillment_id, component, amount, status, "
                + "reason, requested_at, refunded_at) values (?, ?, ?, ?, ?, 'SIMULATED_REFUNDED', 'test', sysdatetime(), sysdatetime())";
        jdbc.update(insert, payment, order, thuDuc, "GOODS", 10);
        jdbc.update(insert, payment, order, null, "SHIPPING", 5);

        assertThatThrownBy(() -> jdbc.update(insert, payment, order, thuDuc, "GOODS", 10)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, payment, order, null, "SHIPPING", 5)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(insert, payment, order, null, "WHOLE_ORDER", 7);
        assertThatThrownBy(() -> jdbc.update(insert, payment, order, null, "WHOLE_ORDER", 7)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, payment, order, thuDuc, "SHIPPING", 5)).as("SHIPPING never names a Fulfillment").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, payment, order, null, "GOODS", 5)).as("GOODS must name a Fulfillment").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, payment, order, null, "WHOLE_ORDER", 0)).as("amount > 0").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, payment, order, null, "BOGUS", 5)).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ================================================================== goods status

    @Test
    @DisplayName("OPEN101-07 UNCONFIRMED keeps the exception open: nothing cancelled, restored or refunded; the hold stays; LOST / RETURNED / redeliver still possible")
    void unconfirmedKeepsTheExceptionOpen() {
        long order = paidTwoStore();
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        long goVap = data.fulfillmentOf(order, "OS-GOVAP");
        failDelivery(STAFF_THUDUC, thuDuc, "Shipper báo không liên lạc được");
        int stock = qty(thuDuc);
        long movements = counts("inventory_movements");
        int orderHistory = data.orderHistory(order);

        Outcome outcome = undeliverable(order, thuDuc, GoodsStatus.UNCONFIRMED, "Đang chờ vận chuyển xác minh hàng");
        Outcome again = undeliverable(order, thuDuc, GoodsStatus.UNCONFIRMED, "Hỏi lại vận chuyển lần hai");

        assertThat(outcome.kind()).isEqualTo(AdminFulfillmentExceptionService.Kind.AWAITING_VERIFICATION);
        assertThat(again.kind()).isEqualTo(AdminFulfillmentExceptionService.Kind.AWAITING_VERIFICATION);
        assertThat(data.fulfillmentStatus(thuDuc)).isEqualTo("FAILED");
        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION");
        assertThat(ledgerRows(order)).isZero();
        assertThat(qty(thuDuc)).isEqualTo(stock);
        assertThat(counts("inventory_movements")).isEqualTo(movements);
        assertThat(data.orderHistory(order)).as("recorded in the history, with the Admin").isEqualTo(orderHistory + 2);
        var part = asAdmin(() -> exceptions.getException(order)).failed().get(0);
        assertThat(part.awaitingVerification()).isTrue();
        assertThat(part.goodsStatus()).isEqualTo("UNCONFIRMED");
        assertThat(part.canMarkUndeliverable()).isTrue();
        assertThat(part.history()).anyMatch(h -> h.note() != null && h.note().contains("[ADMIN_PENDING_VERIFICATION]"));
        // still held: the other Store cannot continue while a failed part is open
        FulfillmentConflictException held = null;
        try {
            act(STAFF_GOVAP, goVap, FulfillmentAction.PREPARE);
        } catch (FulfillmentConflictException ex) {
            held = ex;
        }
        assertThat(held).isNotNull();
        assertThat(held.getCode()).isEqualTo(FulfillmentConflictException.ORDER_NEEDS_ATTENTION);

        // the goods turn up: now it can be decided (returned goods go back into stock exactly once)
        Outcome decided = undeliverable(order, thuDuc, GoodsStatus.RETURNED, "Vận chuyển trả hàng về chi nhánh");
        assertThat(decided.stockRestored()).isTrue();
        assertThat(qty(thuDuc)).isEqualTo(stock + 1);
        assertThat(data.fulfillmentStatus(thuDuc)).isEqualTo("CANCELLED");
        assertThat(data.orderStatus(order)).as("exception resolved: the other part resumes").isEqualTo("CONFIRMED");
        act(STAFF_GOVAP, goVap, FulfillmentAction.PREPARE);
    }

    @Test
    @DisplayName("OPEN101-08 RETURNED restores stock once; LOST never does; the refund is the same either way")
    void returnedVersusLost() {
        for (GoodsStatus goods : List.of(GoodsStatus.RETURNED, GoodsStatus.LOST)) {
            long order = paidTwoStore();
            long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
            long goVap = data.fulfillmentOf(order, "OS-GOVAP");
            completeDelivery(STAFF_GOVAP, goVap);
            failDelivery(STAFF_THUDUC, thuDuc, "Mất liên lạc " + goods);
            int stock = qty(thuDuc);

            undeliverable(order, thuDuc, goods, "Kết quả xác minh " + goods);

            assertThat(qty(thuDuc)).as(goods.name()).isEqualTo(goods == GoodsStatus.RETURNED ? stock + 1 : stock);
            assertThat(jdbc.queryForObject("select count(*) from dbo.inventory_movements where reference_order_id = ? and type = 'CANCEL_ORDER'",
                    Integer.class, order)).isEqualTo(goods == GoodsStatus.RETURNED ? 1 : 0);
            assertThat(refunded(order)).as(goods.name()).isEqualByComparingTo(goodsOf(thuDuc));
        }
    }

    // ================================================================== failed simulation and retry

    @Test
    @DisplayName("OPEN101-09 a failed refund simulation is recorded, moves nothing, keeps the decision, is an Admin task, and a retry completes it once")
    void failedSimulationAndRetry() {
        long order = paidTwoStore();
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        long goVap = data.fulfillmentOf(order, "OS-GOVAP");
        completeDelivery(STAFF_GOVAP, goVap);
        failDelivery(STAFF_THUDUC, thuDuc, "Xe hỏng");
        long attentionBefore = asAdmin(() -> operations.getChainSummary()).ordersNeedingAttention();
        doThrow(new IllegalStateException("simulated bank down")).when(refundSimulator).simulate(any(Payment.class));

        Outcome outcome = undeliverable(order, thuDuc, GoodsStatus.RETURNED, "Không thể giao, hoàn tiền");

        assertThat(outcome.refund().status()).isEqualTo(PartialRefundService.Status.SIMULATION_FAILED);
        assertThat(data.fulfillmentStatus(thuDuc)).as("the decision stands").isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select status from dbo.payment_refunds where order_id = ?", String.class, order)).isEqualTo("SIMULATION_FAILED");
        assertThat(refunded(order)).as("a failed simulation moved nothing").isEqualByComparingTo("0");
        assertThat(category(order)).isEqualTo(PaymentCaseCategory.ATTENTION);
        assertThat(asAdmin(() -> operations.getChainSummary()).ordersNeedingAttention()).as("moves from fulfillment attention to refund attention").isEqualTo(attentionBefore);
        assertThat(asAdmin(() -> adminPayments.getCase(order)).canRetryRefund()).isTrue();

        doNothing().when(refundSimulator).simulate(any(Payment.class));
        asAdmin(() -> adminPayments.retryRefund(order));

        assertThat(jdbc.queryForObject("select status from dbo.payment_refunds where order_id = ?", String.class, order)).isEqualTo("SIMULATED_REFUNDED");
        assertThat(refunded(order)).isEqualByComparingTo(goodsOf(thuDuc));
        assertThat(ledgerRows(order)).as("the same row was retried, not a second refund").isEqualTo(1);
        assertThat(category(order)).isEqualTo(PaymentCaseCategory.PAID);
        assertThat(asAdmin(() -> operations.getChainSummary()).ordersNeedingAttention()).isEqualTo(attentionBefore - 1);
        assertThatThrownBy(() -> asAdmin(() -> adminPayments.retryRefund(order))) // nothing left to retry
                .isInstanceOf(com.oneshop.exception.PaymentConflictException.class);
        assertThat(ledgerRows(order)).isEqualTo(1);
        assertThat(refunded(order)).isEqualByComparingTo(goodsOf(thuDuc));
    }

    // ================================================================== other refund paths and views

    @Test
    @DisplayName("OPEN101-10 the whole-Order refund of a customer cancel is written to the ledger too; the seeded legacy refund is in it; a partial refund blocks a whole one")
    void wholeOrderPathAndLegacy() {
        long legacy = jdbc.queryForObject("select count(*) from dbo.payment_refunds where order_id = 7 and component = 'WHOLE_ORDER' "
                + "and status = 'SIMULATED_REFUNDED' and amount = 388000", Long.class);
        assertThat(legacy).as("the refund seeded on payments.refund_* has its ledger row").isEqualTo(1);

        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);
        data.markPaid(order);
        jdbc.update("update dbo.payments set refund_status = 'NONE' where order_id = ?", order);
        data.asRole(ALICE, "ROLE_CUSTOMER", () -> {
            customerOrders.cancelOrder(ALICE, order);
            return null;
        });
        assertThat(jdbc.queryForObject("select count(*) from dbo.payment_refunds where order_id = ? and component = 'WHOLE_ORDER' "
                + "and status = 'SIMULATED_REFUNDED'", Integer.class, order)).isEqualTo(1);
        assertThat(refunded(order)).isEqualByComparingTo(collected(order));
    }

    @Test
    @DisplayName("OPEN101-11 COD and pay-at-store: no ledger row, ever (nothing was collected for an undelivered part)")
    void codHasNoLedger() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long part = data.onlyFulfillment(order);
        failDelivery(STAFF_THUDUC, part, "Khách từ chối nhận");

        Outcome outcome = undeliverable(order, part, GoodsStatus.RETURNED, "Hàng về kho");

        assertThat(outcome.refund()).isNull();
        assertThat(ledgerRows(order)).isZero();
        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.paymentStatus(order)).isEqualTo("UNPAID");
    }

    @Test
    @DisplayName("OPEN101-12 the views: customer sees own Order only, no staff names, 'mô phỏng'; admin sees who decided; amounts agree")
    void refundViews() {
        long order = paidTwoStore();
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        long goVap = data.fulfillmentOf(order, "OS-GOVAP");
        completeDelivery(STAFF_GOVAP, goVap);
        failDelivery(STAFF_THUDUC, thuDuc, "Xe hỏng giữa đường");
        undeliverable(order, thuDuc, GoodsStatus.RETURNED, "Khách đồng ý hủy phần hàng này");

        OrderRefundSummaryResponse customer = refundView.forCustomer(ALICE, order);
        OrderRefundSummaryResponse admin = asAdmin(() -> refundView.forAdmin(order));

        assertThat(customer.collected()).isEqualByComparingTo(collected(order));
        assertThat(customer.refunded()).isEqualByComparingTo(goodsOf(thuDuc));
        assertThat(customer.retained()).isEqualByComparingTo(collected(order).subtract(goodsOf(thuDuc)));
        assertThat(customer.closedWithUndelivered()).isTrue();
        assertThat(customer.undeliverable()).containsOnlyKeys(thuDuc);
        assertThat(customer.undeliverable().get(thuDuc)).isEqualTo("Khách đồng ý hủy phần hàng này");
        assertThat(customer.lines()).hasSize(1);
        assertThat(customer.lines().get(0).actor()).as("no staff / admin name for the customer").isNull();
        assertThat(customer.lines().get(0).label()).contains("Hoàn tiền hàng không giao được");
        assertThat(admin.lines().get(0).actor()).isNotBlank();
        assertThat(admin.refunded()).isEqualByComparingTo(customer.refunded());
        assertThatThrownBy(() -> refundView.forCustomer(BOB, order)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> refundView.forCustomer(ALICE, 999_999L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ================================================================== concurrency

    private <T> List<Object> race(Callable<T> first, Callable<T> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            Future<Object> fa = pool.submit(() -> attempt(first, ready, go));
            Future<Object> fb = pool.submit(() -> attempt(second, ready, go));
            ready.await();
            go.countDown();
            return List.of(fa.get(), fb.get());
        } finally {
            pool.shutdownNow();
        }
    }

    private static <T> Object attempt(Callable<T> action, CountDownLatch ready, CountDownLatch go) throws Exception {
        ready.countDown();
        go.await();
        try {
            return action.call();
        } catch (Exception ex) {
            return ex;
        }
    }

    @Test
    @DisplayName("OPEN101-13 the same partial refund requested twice at once: one ledger row, one refund, one stock restore")
    void concurrentSamePartialRefund() throws Exception {
        long order = paidTwoStore();
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        long goVap = data.fulfillmentOf(order, "OS-GOVAP");
        completeDelivery(STAFF_GOVAP, goVap);
        failDelivery(STAFF_THUDUC, thuDuc, "Xe hỏng");
        int stock = qty(thuDuc);

        List<Object> results = race(() -> undeliverable(order, thuDuc, GoodsStatus.RETURNED, "Quản trị viên thứ nhất"),
                () -> undeliverable(order, thuDuc, GoodsStatus.RETURNED, "Quản trị viên thứ hai"));

        assertThat(results).filteredOn(r -> r instanceof Outcome).hasSize(1);
        assertThat(results).filteredOn(r -> r instanceof FulfillmentConflictException).hasSize(1);
        assertThat(ledgerRows(order)).isEqualTo(1);
        assertThat(refunded(order)).isEqualByComparingTo(goodsOf(thuDuc));
        assertThat(qty(thuDuc)).isEqualTo(stock + 1);
        assertThat(collected(order).subtract(refunded(order))).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("OPEN101-14 two failed parts decided at once, nothing delivered: total refunded == collected, the shipping fee exactly once")
    void concurrentLastParts() throws Exception {
        long order = paidTwoStore();
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        long goVap = data.fulfillmentOf(order, "OS-GOVAP");
        twoFailedParts(order, thuDuc, goVap);

        List<Object> results = race(() -> undeliverable(order, thuDuc, GoodsStatus.RETURNED, "Quản trị viên thứ nhất xử lý Thủ Đức"),
                () -> undeliverable(order, goVap, GoodsStatus.RETURNED, "Quản trị viên thứ hai xử lý Gò Vấp"));

        assertThat(results).allMatch(r -> r instanceof Outcome);
        assertThat(refunded(order)).isEqualByComparingTo(collected(order));
        assertThat(jdbc.queryForObject("select count(*) from dbo.payment_refunds where order_id = ? and component = 'SHIPPING'", Integer.class, order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from dbo.payment_refunds where order_id = ? and component = 'GOODS'", Integer.class, order)).isEqualTo(2);
        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.paymentStatus(order)).isEqualTo("REFUNDED");
    }
}
