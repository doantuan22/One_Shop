package com.oneshop;

import com.oneshop.dto.response.FulfillmentExceptionResponse;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.FulfillmentConflictException;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.AdminOperationsService;
import com.oneshop.service.CartService;
import com.oneshop.service.CheckoutService;
import com.oneshop.service.fulfillment.AdminFulfillmentExceptionService;
import com.oneshop.service.fulfillment.AdminFulfillmentExceptionService.GoodsStatus;
import com.oneshop.service.fulfillment.AdminFulfillmentExceptionService.Outcome;
import com.oneshop.service.fulfillment.FulfillmentAction;
import com.oneshop.service.fulfillment.StaffFulfillmentService;
import com.oneshop.service.payment.AdminPaymentService;
import com.oneshop.service.payment.PaymentCaseCategory;
import com.oneshop.service.payment.PartialRefundService;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * OPEN-10 on the real SQL Server: how an Administrator closes a failed delivery. Orders are made by the real checkout
 * and driven by the real Staff service; nothing is written with SQL except the ONLINE payment shortcut and the Store
 * switches. These tests commit; {@link SeedGuardV3} puts the test database back, so they only run on a {@code *_test}
 * database. They never touch {@code oneshop}.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=test-only-secret-test-only-secret-1234567890",
        "oneshop.checkout.online-enabled=true",
        "oneshop.payment.timeout-worker-enabled=false"})
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class FulfillmentExceptionDatabaseIntegrationTest {

    static final String ADMIN = "admin@example.com";
    static final String ALICE = "khachhang1@example.com";
    static final String STAFF_THUDUC = "staff.thuduc@example.com";
    static final String STAFF_GOVAP = "staff.govap@example.com";
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

    private void act(String staffEmail, long fulfillment, FulfillmentAction action, String input) {
        data.asStaff(staffEmail, () -> staff.perform(fulfillment, action, input));
    }

    private void act(String staffEmail, long fulfillment, FulfillmentAction action) {
        act(staffEmail, fulfillment, action, null);
    }

    private <T> T asAdmin(Supplier<T> action) {
        return data.asRole(ADMIN, "ROLE_ADMIN", action);
    }

    /** DELIVERY Fulfillment: PREPARE, PACK, SHIP, then the Staff reports that the delivery failed. */
    private void failDelivery(String staffEmail, long fulfillment, String reason) {
        for (FulfillmentAction step : List.of(FulfillmentAction.PREPARE, FulfillmentAction.PACK, FulfillmentAction.SHIP)) {
            act(staffEmail, fulfillment, step, step == FulfillmentAction.SHIP ? "TRK-" + fulfillment : null);
        }
        act(staffEmail, fulfillment, FulfillmentAction.FAIL_DELIVERY, reason);
    }

    /** STORE_PICKUP Fulfillment: PREPARE, READY, then the customer does not collect. */
    private void failPickup(String staffEmail, long fulfillment, String reason) {
        act(staffEmail, fulfillment, FulfillmentAction.PREPARE);
        act(staffEmail, fulfillment, FulfillmentAction.MARK_READY);
        act(staffEmail, fulfillment, FulfillmentAction.FAIL_PICKUP, reason);
    }

    /**
     * Two parts of one Order both FAILED. Staff cannot reach this one by one (the first failure holds the Order), so it is
     * staged: both parts are handed to the carrier, the first one fails for real, the hold is lifted as a concurrent
     * second report would have found it, and the second one fails for real. The Service must cope with it all the same.
     */
    private void twoFailedParts(long order, long first, long second) {
        for (long part : new long[] {first, second}) {
            String staffEmail = part == first ? STAFF_THUDUC : STAFF_GOVAP;
            for (FulfillmentAction step : List.of(FulfillmentAction.PREPARE, FulfillmentAction.PACK, FulfillmentAction.SHIP)) {
                act(staffEmail, part, step, step == FulfillmentAction.SHIP ? "TRK-" + part : null);
            }
        }
        act(STAFF_THUDUC, first, FulfillmentAction.FAIL_DELIVERY, "Khách vắng nhà");
        jdbc.update("update dbo.orders set order_status = 'SHIPPING' where order_id = ?", order);
        act(STAFF_GOVAP, second, FulfillmentAction.FAIL_DELIVERY, "Khách vắng nhà");
    }

    private void completeDelivery(String staffEmail, long fulfillment) {
        for (FulfillmentAction step : List.of(FulfillmentAction.PREPARE, FulfillmentAction.PACK, FulfillmentAction.SHIP,
                FulfillmentAction.COMPLETE_DELIVERY)) {
            act(staffEmail, fulfillment, step);
        }
    }

    private FulfillmentExceptionResponse view(long order) {
        return asAdmin(() -> exceptions.getException(order));
    }

    private Outcome redeliver(long order, long fulfillment, boolean confirmed, String reason) {
        return asAdmin(() -> exceptions.redeliver(order, fulfillment, confirmed, reason));
    }

    private Outcome undeliverable(long order, long fulfillment, GoodsStatus goods, String reason) {
        return asAdmin(() -> exceptions.markUndeliverable(order, fulfillment, goods, reason));
    }

    private FulfillmentConflictException conflict(Runnable action) {
        try {
            action.run();
        } catch (FulfillmentConflictException ex) {
            return ex;
        }
        throw new AssertionError("expected a FulfillmentConflictException");
    }

    private int movementsOf(long order) {
        return jdbc.queryForObject("select count(*) from dbo.inventory_movements where reference_order_id = ? "
                + "and type = 'CANCEL_ORDER'", Integer.class, order);
    }

    private long counts(String table) {
        return jdbc.queryForObject("select count(*) from dbo." + table, Long.class);
    }

    private int qty(long storeProduct) {
        return jdbc.queryForObject("select quantity from dbo.store_products where store_product_id = ?", Integer.class,
                storeProduct);
    }

    private long storeProductOf(long fulfillment) {
        return jdbc.queryForObject("select top 1 store_product_id from dbo.order_items where fulfillment_id = ? "
                + "order by order_item_id", Long.class, fulfillment);
    }

    private String refundStatus(long order) {
        return jdbc.queryForObject("select top 1 refund_status from dbo.payments where order_id = ? order by payment_id desc",
                String.class, order);
    }

    private PaymentCaseCategory category(long order) {
        return asAdmin(() -> adminPayments.getCases(null, String.valueOf(order), 0)).getContent().stream()
                .filter(c -> c.orderId() == order).findFirst().orElseThrow().category();
    }

    private long attention() {
        return asAdmin(() -> operations.getChainSummary()).ordersNeedingAttention();
    }

    // ================================================================== the failure itself and what Admin sees

    @Test
    @DisplayName("OPEN10-01 a failed COD delivery holds the Order; Admin sees the reason, the goods, the stock and the money")
    void failedCodDeliveryIsVisibleToAdmin() {
        long attentionBefore = attention();
        long order = data.codDelivery("OS-THUDUC", SERUM, 2);
        long part = data.onlyFulfillment(order);
        failDelivery(STAFF_THUDUC, part, "Khách không nhận hàng, đã gọi 3 lần");

        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION");
        assertThat(attention()).isEqualTo(attentionBefore + 1);
        FulfillmentExceptionResponse exception = view(order);
        assertThat(exception).isNotNull();
        assertThat(exception.failed()).hasSize(1);
        var failed = exception.failed().get(0);
        assertThat(failed.fulfillmentId()).isEqualTo(part);
        assertThat(failed.storeName()).contains("Thủ Đức");
        assertThat(failed.failureReason()).contains("Khách không nhận hàng");
        assertThat(failed.failedBy()).isNotBlank();
        assertThat(failed.lines()).hasSize(1);
        assertThat(failed.lines().get(0).quantity()).isEqualTo(2);
        assertThat(failed.lines().get(0).stockNow()).isEqualTo(qty(storeProductOf(part)));
        assertThat(failed.canRedeliver()).isTrue();
        assertThat(failed.canMarkUndeliverable()).isTrue();
        assertThat(exception.money().collected()).as("COD collects only when delivery succeeds").isEqualByComparingTo("0");
        assertThat(exception.money().undelivered()).isGreaterThan(java.math.BigDecimal.ZERO);
        assertThat(exception.money().refunded()).isEqualByComparingTo("0");
        assertThat(view(data.codDelivery("OS-THUDUC", SHAMPOO, 1))).as("a healthy Order has no exception").isNull();
    }

    // ================================================================== A: redeliver

    @Test
    @DisplayName("OPEN10-02 redeliver a DELIVERY part: FAILED to PACKED, no stock, payment or order change; Staff finish the delivery")
    void redeliverDelivery() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long part = data.onlyFulfillment(order);
        failDelivery(STAFF_THUDUC, part, "Xe giao hàng gặp sự cố");
        long stockBefore = qty(storeProductOf(part));
        long movements = counts("inventory_movements");
        long orders = counts("orders");
        long payments = counts("payments");

        Outcome outcome = redeliver(order, part, true, "Đã xác minh hàng còn nguyên tại chi nhánh");

        assertThat(outcome.kind()).isEqualTo(AdminFulfillmentExceptionService.Kind.REDELIVERY_OPENED);
        assertThat(data.fulfillmentStatus(part)).isEqualTo("PACKED");
        assertThat(data.orderStatus(order)).isEqualTo("PROCESSING");
        assertThat(qty(storeProductOf(part))).as("the stock deducted at checkout is still held: nothing deducted twice").isEqualTo((int) stockBefore);
        assertThat(counts("inventory_movements")).isEqualTo(movements);
        assertThat(counts("orders")).isEqualTo(orders);
        assertThat(counts("payments")).isEqualTo(payments);
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_fulfillment_status_history where fulfillment_id = ? "
                + "and new_status = 'PACKED' and note like '\\[ADMIN_REDELIVER]%' escape '\\' and changed_by_user_id = (select user_id "
                + "from dbo.users where email = ?)", Integer.class, part, ADMIN)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_status_history where order_id = ? and old_status = "
                + "'NEEDS_ATTENTION' and new_status = 'PROCESSING' and note like '\\[ADMIN_REDELIVER]%' escape '\\'", Integer.class, order)).isEqualTo(1);
        assertThat(view(order)).isNull();

        // Staff carry on: the tracking code of the failed attempt is gone, a new hand-over is recorded and COD is collected once
        act(STAFF_THUDUC, part, FulfillmentAction.SHIP, "TRK-RETRY");
        act(STAFF_THUDUC, part, FulfillmentAction.COMPLETE_DELIVERY);
        assertThat(data.orderStatus(order)).isEqualTo("COMPLETED");
        assertThat(data.paymentStatus(order)).isEqualTo("PAID");
        assertThat(data.successfulPayments(order)).isEqualTo(1);
    }

    @Test
    @DisplayName("OPEN10-03 redeliver an ONLINE pickup that was not collected: back to READY_FOR_PICKUP with the same code; Staff complete it")
    void redeliverPickup() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);
        data.markPaid(order);
        long part = data.onlyFulfillment(order);
        failPickup(STAFF_THUDUC, part, "Khách không đến nhận sau 3 ngày");
        String code = data.pickupCode(part);
        assertThat(code).isNotBlank();

        redeliver(order, part, true, "Khách hẹn nhận lại, hàng vẫn giữ tại quầy");

        assertThat(data.fulfillmentStatus(part)).isEqualTo("READY_FOR_PICKUP");
        assertThat(data.pickupCode(part)).isEqualTo(code);
        assertThat(data.orderStatus(order)).isEqualTo("PROCESSING");
        assertThat(data.successfulPayments(order)).isEqualTo(1);
        act(STAFF_THUDUC, part, FulfillmentAction.COMPLETE_PICKUP, code);
        assertThat(data.orderStatus(order)).isEqualTo("COMPLETED");
        assertThat(data.paymentStatus(order)).isEqualTo("PAID");
        assertThat(data.successfulPayments(order)).as("never a second payment").isEqualTo(1);
    }

    @Test
    @DisplayName("OPEN10-04 redeliver needs a reason, the goods confirmation and an active Store that still offers the service")
    void redeliverValidation() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long part = data.onlyFulfillment(order);
        failDelivery(STAFF_THUDUC, part, "Địa chỉ không tồn tại");

        for (String reason : new String[] {null, "", "  ", "abc", "x".repeat(401)}) {
            assertThatThrownBy(() -> redeliver(order, part, true, reason)).isInstanceOf(BadRequestException.class);
        }
        assertThatThrownBy(() -> redeliver(order, part, false, "Có lý do hợp lệ")).isInstanceOf(BadRequestException.class);
        assertThat(data.fulfillmentStatus(part)).isEqualTo("FAILED");

        jdbc.update("update dbo.stores set delivery_enabled = 0 where code = 'OS-THUDUC'");
        assertThat(view(order).failed().get(0).canRedeliver()).isFalse();
        assertThat(view(order).failed().get(0).redeliverBlocked()).contains("giao hàng tận nơi");
        assertThat(conflict(() -> redeliver(order, part, true, "Thử khi chi nhánh tắt giao hàng")).getCode())
                .isEqualTo(AdminFulfillmentExceptionService.REDELIVERY_NOT_POSSIBLE);
        jdbc.update("update dbo.stores set delivery_enabled = 1, status = 'INACTIVE' where code = 'OS-THUDUC'");
        assertThat(conflict(() -> redeliver(order, part, true, "Thử khi chi nhánh ngừng hoạt động")).getCode())
                .isEqualTo(AdminFulfillmentExceptionService.REDELIVERY_NOT_POSSIBLE);
        assertThat(data.fulfillmentStatus(part)).as("every refusal wrote nothing").isEqualTo("FAILED");
        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION");
    }

    // ================================================================== B: undeliverable

    @Test
    @DisplayName("OPEN10-05 undeliverable, goods RETURNED (COD): stock restored once, part CANCELLED, Order CANCELLED, no refund")
    void undeliverableReturnedCod() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 2);
        long part = data.onlyFulfillment(order);
        failDelivery(STAFF_THUDUC, part, "Khách từ chối nhận hàng");
        long sp = storeProductOf(part);
        int before = qty(sp);

        Outcome outcome = undeliverable(order, part, GoodsStatus.RETURNED, "Hàng đã về kho chi nhánh, kiểm nguyên vẹn");

        assertThat(outcome.orderClosed()).isTrue();
        assertThat(outcome.stockRestored()).isTrue();
        assertThat(outcome.refund()).isNull();
        assertThat(qty(sp)).isEqualTo(before + 2);
        assertThat(movementsOf(order)).isEqualTo(1);
        assertThat(data.fulfillmentStatus(part)).isEqualTo("CANCELLED");
        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.paymentStatus(order)).as("nothing was ever collected").isEqualTo("UNPAID");
        assertThat(data.successfulPayments(order)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from dbo.payments where order_id = ? and refund_status <> 'NONE'",
                Integer.class, order)).isZero();
        assertThat(jdbc.queryForObject("select cancel_reason from dbo.order_fulfillments where fulfillment_id = ?",
                String.class, part)).startsWith("Không giao được: ").contains("Hàng đã về kho chi nhánh");
        assertThat(jdbc.queryForObject("select note from dbo.order_fulfillments where fulfillment_id = ?", String.class, part))
                .contains("[ADMIN_UNDELIVERABLE]", "[GOODS:RETURNED]");
        assertThat(view(order)).isNull();
    }

    @Test
    @DisplayName("OPEN10-06 goods LOST: the part is closed and stock is never changed; UNCONFIRMED (OPEN-10.1) does not close anything")
    void undeliverableLostOrUnconfirmed() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long part = data.onlyFulfillment(order);
        failDelivery(STAFF_THUDUC, part, "Shipper báo mất liên lạc");
        long sp = storeProductOf(part);
        int before = qty(sp);
        long movements = counts("inventory_movements");

        // OPEN-10.1: "unconfirmed" is a note, not a decision: the exception stays open
        Outcome pending = undeliverable(order, part, GoodsStatus.UNCONFIRMED, "Chưa có hàng trả về, đang liên hệ vận chuyển");
        assertThat(pending.kind()).isEqualTo(AdminFulfillmentExceptionService.Kind.AWAITING_VERIFICATION);
        assertThat(data.fulfillmentStatus(part)).isEqualTo("FAILED");
        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION");
        assertThat(qty(sp)).isEqualTo(before);
        assertThat(counts("inventory_movements")).isEqualTo(movements);
        assertThat(view(order).failed().get(0).awaitingVerification()).isTrue();

        Outcome outcome = undeliverable(order, part, GoodsStatus.LOST, "Vận chuyển xác nhận thất lạc");

        assertThat(outcome.stockRestored()).isFalse();
        assertThat(qty(sp)).isEqualTo(before);
        assertThat(counts("inventory_movements")).isEqualTo(movements);
        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.fulfillmentStatus(part)).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("OPEN10-07 ONLINE paid, nothing delivered: the simulated refund is recorded once (whole amount), stock follows the goods")
    void undeliverableOnlinePaid() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);
        data.markPaid(order);
        jdbc.update("update dbo.payments set refund_status = 'NONE' where order_id = ?", order);
        long part = data.onlyFulfillment(order);
        failPickup(STAFF_THUDUC, part, "Khách không nhận hàng");
        long payments = counts("payments");

        Outcome outcome = undeliverable(order, part, GoodsStatus.RETURNED, "Khách hủy nhận, hàng còn tại quầy");

        assertThat(outcome.refund().status()).isEqualTo(PartialRefundService.Status.REFUNDED);
        assertThat(outcome.refund().fullyRefunded()).isTrue();
        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.paymentStatus(order)).isEqualTo("REFUNDED");
        assertThat(refundStatus(order)).isEqualTo("SIMULATED_REFUNDED");
        assertThat(jdbc.queryForObject("select refund_amount from dbo.payments where order_id = ?", java.math.BigDecimal.class, order))
                .isEqualByComparingTo(jdbc.queryForObject("select total_amount from dbo.orders where order_id = ?",
                        java.math.BigDecimal.class, order));
        assertThat(counts("payments")).as("no second payment row").isEqualTo(payments);
        assertThat(category(order)).isEqualTo(PaymentCaseCategory.REFUNDED);
        assertThat(movementsOf(order)).isEqualTo(1);
    }

    @Test
    @DisplayName("OPEN10-08 pay-at-store pickup that was never collected: nothing is refunded")
    void undeliverablePayAtStore() {
        long order = data.payAtStorePickup("OS-THUDUC", SERUM, 1);
        long part = data.onlyFulfillment(order);
        failPickup(STAFF_THUDUC, part, "Khách không đến");

        undeliverable(order, part, GoodsStatus.RETURNED, "Hàng còn tại quầy, nhập lại kho");

        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.paymentStatus(order)).isEqualTo("UNPAID");
        assertThat(data.successfulPayments(order)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from dbo.payments where order_id = ? and refund_status <> 'NONE'",
                Integer.class, order)).isZero();
    }

    @Test
    @DisplayName("OPEN10-09 undeliverable needs a reason and a goods status; an unknown Fulfillment of the Order is 404")
    void undeliverableValidation() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long part = data.onlyFulfillment(order);
        failDelivery(STAFF_THUDUC, part, "Khách vắng nhà");

        for (String reason : new String[] {null, "", "ab", "y".repeat(401)}) {
            assertThatThrownBy(() -> undeliverable(order, part, GoodsStatus.LOST, reason)).isInstanceOf(BadRequestException.class);
        }
        assertThatThrownBy(() -> undeliverable(order, part, null, "Có lý do hợp lệ")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> undeliverable(order, 999_999L, GoodsStatus.LOST, "Có lý do hợp lệ"))
                .isInstanceOf(com.oneshop.exception.ResourceNotFoundException.class);
        long other = data.onlyFulfillment(data.codDelivery("OS-THUDUC", SHAMPOO, 1));
        long movements = counts("inventory_movements");
        assertThatThrownBy(() -> undeliverable(order, other, GoodsStatus.LOST, "Phần giao của đơn khác"))
                .isInstanceOf(com.oneshop.exception.ResourceNotFoundException.class);
        assertThat(data.fulfillmentStatus(part)).isEqualTo("FAILED");
        assertThat(counts("inventory_movements")).isEqualTo(movements);
    }

    // ================================================================== several Fulfillments

    @Test
    @DisplayName("OPEN10-10 one part FAILED, another still going (ONLINE): undeliverable now refunds only that part's goods (shipping kept); the other part continues")
    void mixedOrderRefundsOnlyThePart() {
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        data.markPaid(order);
        jdbc.update("update dbo.payments set refund_status = 'NONE' where order_id = ?", order);
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        long goVap = data.fulfillmentOf(order, "OS-GOVAP");
        failDelivery(STAFF_THUDUC, thuDuc, "Sai địa chỉ");
        var failed = view(order).failed().get(0);
        assertThat(failed.canMarkUndeliverable()).isTrue();
        assertThat(failed.refundShipping()).as("another part is not finished: the shipping fee is kept for now").isEqualByComparingTo("0");
        java.math.BigDecimal goods = jdbc.queryForObject("select sum(subtotal) from dbo.order_items where fulfillment_id = ?",
                java.math.BigDecimal.class, thuDuc);
        long payments = counts("payments");

        Outcome outcome = undeliverable(order, thuDuc, GoodsStatus.RETURNED, "Khách hủy nhận phần hàng này");

        assertThat(outcome.refund().goods()).isEqualByComparingTo(goods);
        assertThat(outcome.refund().shipping()).isEqualByComparingTo("0");
        assertThat(outcome.refund().fullyRefunded()).isFalse();
        assertThat(data.fulfillmentStatus(thuDuc)).isEqualTo("CANCELLED");
        assertThat(data.fulfillmentStatus(goVap)).as("the other Store's part is never touched").isEqualTo("CONFIRMED");
        assertThat(data.orderStatus(order)).as("work is still left: the Order resumes").isEqualTo("CONFIRMED");
        assertThat(data.paymentStatus(order)).as("only part of the money went back").isEqualTo("PAID");
        assertThat(counts("payments")).isEqualTo(payments);
        assertThat(jdbc.queryForObject("select count(*) from dbo.payment_refunds where order_id = ? and component = 'GOODS' "
                + "and fulfillment_id = ? and status = 'SIMULATED_REFUNDED'", Integer.class, order, thuDuc)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from dbo.payment_refunds where order_id = ? and component = 'SHIPPING'",
                Integer.class, order)).isZero();
        assertThat(refundStatus(order)).isEqualTo("NONE");

        // Staff carry on with the other part; the Order completes with one part undelivered, shipping kept
        completeDelivery(STAFF_GOVAP, goVap);
        assertThat(data.orderStatus(order)).isEqualTo("COMPLETED");
        assertThat(data.paymentStatus(order)).isEqualTo("PAID");
        assertThat(data.successfulPayments(order)).isEqualTo(1);
    }

    @Test
    @DisplayName("OPEN10-11 a part already COMPLETED stays COMPLETED; the failed one is refunded by value, the shipping fee is kept; the Order closes COMPLETED")
    void completedPartIsNeverTouched() {
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        data.markPaid(order);
        jdbc.update("update dbo.payments set refund_status = 'NONE' where order_id = ?", order);
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        long goVap = data.fulfillmentOf(order, "OS-GOVAP");
        completeDelivery(STAFF_GOVAP, goVap);
        failDelivery(STAFF_THUDUC, thuDuc, "Xe hỏng giữa đường");
        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION");
        var exception = view(order);
        assertThat(exception.money().delivered()).isGreaterThan(java.math.BigDecimal.ZERO);
        java.math.BigDecimal collected = exception.money().collected();

        Outcome outcome = undeliverable(order, thuDuc, GoodsStatus.RETURNED, "Xin hủy phần này");

        assertThat(outcome.partiallyClosed()).isTrue();
        assertThat(outcome.refund().shipping()).as("something was delivered: the shipping fee is kept").isEqualByComparingTo("0");
        assertThat(data.fulfillmentStatus(goVap)).isEqualTo("COMPLETED");
        assertThat(data.fulfillmentStatus(thuDuc)).isEqualTo("CANCELLED");
        assertThat(data.orderStatus(order)).isEqualTo("COMPLETED");
        assertThat(data.paymentStatus(order)).isEqualTo("PAID");
        java.math.BigDecimal refunded = jdbc.queryForObject("select sum(amount) from dbo.payment_refunds where order_id = ?",
                java.math.BigDecimal.class, order);
        assertThat(refunded).isEqualByComparingTo(outcome.refund().goods()).isLessThan(collected);
        assertThat(collected.subtract(refunded)).as("kept = delivered goods + one shipping fee")
                .isEqualByComparingTo(exception.money().delivered().add(jdbc.queryForObject(
                        "select shipping_fee from dbo.orders where order_id = ?", java.math.BigDecimal.class, order)));
    }

    @Test
    @DisplayName("OPEN10-12 two FAILED parts: the Order stays NEEDS_ATTENTION until both are decided; refund and stock happen once each")
    void twoFailedParts() {
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        data.markPaid(order);
        jdbc.update("update dbo.payments set refund_status = 'NONE' where order_id = ?", order);
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        long goVap = data.fulfillmentOf(order, "OS-GOVAP");
        twoFailedParts(order, thuDuc, goVap);
        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION");
        assertThat(view(order).failed()).hasSize(2);
        assertThat(view(order).failed()).allMatch(f -> f.canMarkUndeliverable());
        int s1 = qty(storeProductOf(thuDuc));
        int s2 = qty(storeProductOf(goVap));

        Outcome first = undeliverable(order, thuDuc, GoodsStatus.RETURNED, "Hàng về kho Thủ Đức");
        assertThat(first.orderClosed()).isFalse();
        assertThat(first.failedLeft()).isEqualTo(1);
        assertThat(data.orderStatus(order)).as("one decision left: the hold stays").isEqualTo("NEEDS_ATTENTION");
        assertThat(first.refund().shipping()).as("the other part is not decided yet: shipping not refunded yet").isEqualByComparingTo("0");
        assertThat(data.paymentStatus(order)).as("not everything is back yet").isEqualTo("PAID");
        assertThat(refundStatus(order)).isEqualTo("NONE");

        Outcome second = undeliverable(order, goVap, GoodsStatus.LOST, "Hàng thất lạc ở Gò Vấp");
        assertThat(second.orderClosed()).isTrue();
        assertThat(second.refund().status()).isEqualTo(PartialRefundService.Status.REFUNDED);
        assertThat(second.refund().shipping()).as("nothing was delivered: the one shipping fee goes back now").isGreaterThan(java.math.BigDecimal.ZERO);
        assertThat(second.refund().fullyRefunded()).isTrue();
        assertThat(jdbc.queryForObject("select sum(amount) from dbo.payment_refunds where order_id = ?", java.math.BigDecimal.class,
                order)).isEqualByComparingTo(jdbc.queryForObject("select total_amount from dbo.orders where order_id = ?",
                java.math.BigDecimal.class, order));
        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(refundStatus(order)).isEqualTo("SIMULATED_REFUNDED");
        assertThat(qty(storeProductOf(thuDuc))).isEqualTo(s1 + 1);
        assertThat(qty(storeProductOf(goVap))).as("lost goods are not restocked").isEqualTo(s2);
        assertThat(movementsOf(order)).isEqualTo(1);
    }

    @Test
    @DisplayName("OPEN10-13 two FAILED parts, one redelivered: the Order stays NEEDS_ATTENTION (audit row only) until the last decision")
    void redeliverOneOfTwo() {
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        data.markPaid(order);
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        long goVap = data.fulfillmentOf(order, "OS-GOVAP");
        twoFailedParts(order, thuDuc, goVap);

        Outcome outcome = redeliver(order, thuDuc, true, "Đã liên hệ lại khách");

        assertThat(outcome.failedLeft()).isEqualTo(1);
        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION");
        assertThat(data.fulfillmentStatus(thuDuc)).isEqualTo("PACKED");
        assertThat(data.fulfillmentStatus(goVap)).isEqualTo("FAILED");
        assertThat(view(order).failed()).extracting(f -> f.fulfillmentId()).containsExactly(goVap);

        redeliver(order, goVap, true, "Khách xác nhận có nhà");
        assertThat(data.orderStatus(order)).isEqualTo("PROCESSING");
    }

    // ================================================================== guards and idempotency

    @Test
    @DisplayName("OPEN10-14 a decision is taken once: repeating it changes nothing (no second restore, refund, history or payment)")
    void repeatedDecisionIsIdempotent() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);
        data.markPaid(order);
        long part = data.onlyFulfillment(order);
        failPickup(STAFF_THUDUC, part, "Khách không nhận");
        undeliverable(order, part, GoodsStatus.RETURNED, "Hàng còn tại quầy");
        long movements = counts("inventory_movements");
        long payments = counts("payments");
        int history = data.orderHistory(order);
        int partHistory = data.fulfillmentHistory(part);
        int stock = qty(storeProductOf(part));

        for (int i = 0; i < 3; i++) {
            assertThat(conflict(() -> undeliverable(order, part, GoodsStatus.RETURNED, "Gửi lại lần nữa")).getCode())
                    .isEqualTo(AdminFulfillmentExceptionService.ALREADY_RESOLVED);
            assertThat(conflict(() -> redeliver(order, part, true, "Giao lại sau khi đã hủy")).getCode())
                    .isEqualTo(AdminFulfillmentExceptionService.ALREADY_RESOLVED);
        }
        assertThat(counts("inventory_movements")).isEqualTo(movements);
        assertThat(counts("payments")).isEqualTo(payments);
        assertThat(data.orderHistory(order)).isEqualTo(history);
        assertThat(data.fulfillmentHistory(part)).isEqualTo(partHistory);
        assertThat(qty(storeProductOf(part))).isEqualTo(stock);
        assertThat(refundStatus(order)).isEqualTo("SIMULATED_REFUNDED");
    }

    @Test
    @DisplayName("OPEN10-15 an Order that is NEEDS_ATTENTION for a payment reason has no delivery exception and cannot be decided here")
    void paymentAttentionIsNotADeliveryFailure() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);
        jdbc.update("update dbo.orders set order_status = 'NEEDS_ATTENTION' where order_id = ?", order);
        long part = data.onlyFulfillment(order);

        assertThat(view(order)).isNull();
        assertThat(conflict(() -> redeliver(order, part, true, "Không phải giao thất bại")).getCode())
                .isEqualTo(AdminFulfillmentExceptionService.ALREADY_RESOLVED);
        assertThat(conflict(() -> undeliverable(order, part, GoodsStatus.LOST, "Không phải giao thất bại")).getCode())
                .isEqualTo(AdminFulfillmentExceptionService.ALREADY_RESOLVED);
        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION");
    }

    @Test
    @DisplayName("OPEN10-16 only an Administrator can read or decide; Staff and Customer are refused and nothing changes")
    void onlyAdministrators() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long part = data.onlyFulfillment(order);
        failDelivery(STAFF_THUDUC, part, "Khách vắng nhà");

        for (String[] who : new String[][] {{STAFF_THUDUC, "ROLE_STAFF"}, {ALICE, "ROLE_CUSTOMER"}}) {
            assertThatThrownBy(() -> data.asRole(who[0], who[1], () -> exceptions.getException(order)))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> data.asRole(who[0], who[1], () -> exceptions.redeliver(order, part, true, "Thử với vai trò " + who[1])))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> data.asRole(who[0], who[1], () -> exceptions.markUndeliverable(order, part, GoodsStatus.LOST, "Thử với vai trò " + who[1])))
                    .isInstanceOf(AccessDeniedException.class);
        }
        assertThat(data.fulfillmentStatus(part)).isEqualTo("FAILED");
        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION");
    }

    @Test
    @DisplayName("OPEN10-17 the Dashboard 'Cần Admin xử lý' and the Admin attention list follow the decision, counting the Order once")
    void dashboardFollowsTheDecision() {
        long before = attention();
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        data.markPaid(order);
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        long goVap = data.fulfillmentOf(order, "OS-GOVAP");
        twoFailedParts(order, thuDuc, goVap);

        assertThat(attention()).as("two failed parts, one Order").isEqualTo(before + 1);
        assertThat(category(order)).isEqualTo(PaymentCaseCategory.ATTENTION);
        assertThat(asAdmin(() -> operations.getOrdersNeedingAttention(null, 0)).getContent())
                .extracting(o -> o.order().orderId()).contains(order);

        redeliver(order, thuDuc, true, "Xác nhận lại địa chỉ");
        assertThat(attention()).as("one decision left").isEqualTo(before + 1);
        redeliver(order, goVap, true, "Xác nhận lại địa chỉ");

        assertThat(attention()).isEqualTo(before);
        assertThat(category(order)).isEqualTo(PaymentCaseCategory.PAID);
        assertThat(asAdmin(() -> operations.getOrdersNeedingAttention(null, 0)).getContent())
                .extracting(o -> o.order().orderId()).doesNotContain(order);
    }

    // ================================================================== concurrency

    private <T> List<Object> race(Callable<T> first, Callable<T> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            Callable<Object> a = () -> attempt(first, ready, go);
            Callable<Object> b = () -> attempt(second, ready, go);
            Future<Object> fa = pool.submit(a);
            Future<Object> fb = pool.submit(b);
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
    @DisplayName("OPEN10-18 the same 'undeliverable' sent twice at once: one effect (one restore, one refund), the other is a clean conflict")
    void concurrentSameDecision() throws Exception {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);
        data.markPaid(order);
        jdbc.update("update dbo.payments set refund_status = 'NONE' where order_id = ?", order);
        long part = data.onlyFulfillment(order);
        failPickup(STAFF_THUDUC, part, "Khách không nhận");
        int stock = qty(storeProductOf(part));

        List<Object> results = race(
                () -> undeliverable(order, part, GoodsStatus.RETURNED, "Yêu cầu từ quản trị viên thứ nhất"),
                () -> undeliverable(order, part, GoodsStatus.RETURNED, "Yêu cầu từ quản trị viên thứ hai"));

        assertThat(results).filteredOn(r -> r instanceof Outcome).hasSize(1);
        assertThat(results).filteredOn(r -> r instanceof FulfillmentConflictException).hasSize(1);
        assertThat(qty(storeProductOf(part))).isEqualTo(stock + 1);
        assertThat(movementsOf(order)).isEqualTo(1);
        assertThat(refundStatus(order)).isEqualTo("SIMULATED_REFUNDED");
        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_status_history where order_id = ? and new_status = "
                + "'CANCELLED'", Integer.class, order)).isEqualTo(1);
    }

    @Test
    @DisplayName("OPEN10-19 redeliver against undeliverable at once: exactly one wins and the Order is consistent")
    void concurrentOpposingDecisions() throws Exception {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long part = data.onlyFulfillment(order);
        failDelivery(STAFF_THUDUC, part, "Khách vắng nhà");
        int stock = qty(storeProductOf(part));

        List<Object> results = race(
                () -> redeliver(order, part, true, "Giao lại theo yêu cầu khách"),
                () -> undeliverable(order, part, GoodsStatus.RETURNED, "Không giao được theo yêu cầu khách"));

        assertThat(results).filteredOn(r -> r instanceof Outcome).hasSize(1);
        assertThat(results).filteredOn(r -> r instanceof FulfillmentConflictException).hasSize(1);
        String partStatus = data.fulfillmentStatus(part);
        if (partStatus.equals("PACKED")) {
            assertThat(data.orderStatus(order)).isEqualTo("PROCESSING");
            assertThat(qty(storeProductOf(part))).isEqualTo(stock);
            assertThat(movementsOf(order)).isZero();
        } else {
            assertThat(partStatus).isEqualTo("CANCELLED");
            assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
            assertThat(qty(storeProductOf(part))).isEqualTo(stock + 1);
            assertThat(movementsOf(order)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("OPEN10-20 two different failed parts decided at once: both applied, refund once, never half-closed")
    void concurrentDifferentParts() throws Exception {
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        data.markPaid(order);
        jdbc.update("update dbo.payments set refund_status = 'NONE' where order_id = ?", order);
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        long goVap = data.fulfillmentOf(order, "OS-GOVAP");
        twoFailedParts(order, thuDuc, goVap);

        List<Object> results = race(
                () -> undeliverable(order, thuDuc, GoodsStatus.RETURNED, "Quản trị viên thứ nhất xử lý phần Thủ Đức"),
                () -> undeliverable(order, goVap, GoodsStatus.RETURNED, "Quản trị viên thứ hai xử lý phần Gò Vấp"));

        assertThat(results).allMatch(r -> r instanceof Outcome);
        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.fulfillmentStatus(thuDuc)).isEqualTo("CANCELLED");
        assertThat(data.fulfillmentStatus(goVap)).isEqualTo("CANCELLED");
        assertThat(refundStatus(order)).isEqualTo("SIMULATED_REFUNDED");
        assertThat(movementsOf(order)).as("one restore per part").isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_status_history where order_id = ? and new_status = "
                + "'CANCELLED'", Integer.class, order)).isEqualTo(1);
    }
}
