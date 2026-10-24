package com.oneshop;

import com.oneshop.dto.response.FulfillmentDetailResponse;
import com.oneshop.dto.response.FulfillmentQueueItemResponse;
import com.oneshop.dto.response.OrderDetailResponse;
import com.oneshop.dto.response.StaffDashboardResponse;
import com.oneshop.dto.response.StaffStoreDashboardResponse;
import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.OrderFulfillmentStatus;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.FulfillmentConflictException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.CartService;
import com.oneshop.service.CheckoutService;
import com.oneshop.service.CustomerOrderService;
import com.oneshop.service.StaffOperationsService;
import com.oneshop.service.fulfillment.FulfillmentAction;
import com.oneshop.service.fulfillment.FulfillmentQueueFilter;
import com.oneshop.service.fulfillment.StaffFulfillmentService;
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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static com.oneshop.FulfillmentFixtures.ALICE;
import static com.oneshop.FulfillmentFixtures.BOB;
import static com.oneshop.FulfillmentFixtures.CHEEK;
import static com.oneshop.FulfillmentFixtures.SERUM;
import static com.oneshop.FulfillmentFixtures.SHAMPOO;
import static com.oneshop.FulfillmentFixtures.STAFF_GOVAP;
import static com.oneshop.FulfillmentFixtures.STAFF_QUAN10;
import static com.oneshop.FulfillmentFixtures.STAFF_QUAN7;
import static com.oneshop.FulfillmentFixtures.STAFF_THUDUC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * V3-BE-04 on the real SQL Server: Staff authorization by {@code order_fulfillments.store_id}, the DELIVERY and
 * STORE_PICKUP state machines, pickup code, COD / PAY_AT_STORE collection, payment gate, Order aggregation and the
 * guarantee that no status change touches stock. Orders are made by the real checkout.
 *
 * <p>These tests commit; {@link SeedGuardV3} puts the test database back. Only against a {@code *_test} database.
 * {@code oneshop.checkout.online-enabled=true} is a TEST-ONLY switch (ONLINE checkout is closed until BE-05).
 */
@SpringBootTest(properties = {
        "app.jwt.secret=test-only-secret-test-only-secret-1234567890",
        "oneshop.checkout.online-enabled=true"})
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class FulfillmentOperationsDatabaseIntegrationTest {

    @Autowired
    StaffFulfillmentService staff;
    @Autowired
    StaffOperationsService operations;
    @Autowired
    CustomerOrderService customerOrders;
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

    private FulfillmentDetailResponse detail(String staffEmail, long fulfillmentId) {
        return data.asStaff(staffEmail, () -> staff.getFulfillment(fulfillmentId));
    }

    private List<FulfillmentQueueItemResponse> queue(String staffEmail, FulfillmentQueueFilter filter) {
        return data.asStaff(staffEmail, () -> staff.getQueue(filter, 0)).getContent();
    }

    /** Runs the action once and returns the business conflict it must end with. */
    private FulfillmentConflictException conflict(Runnable action) {
        try {
            action.run();
        } catch (FulfillmentConflictException ex) {
            return ex;
        }
        throw new AssertionError("expected a FulfillmentConflictException");
    }

    private void driveDeliveryTo(String staffEmail, long fulfillmentId, FulfillmentAction last) {
        for (FulfillmentAction step : List.of(FulfillmentAction.PREPARE, FulfillmentAction.PACK,
                FulfillmentAction.SHIP, FulfillmentAction.COMPLETE_DELIVERY)) {
            act(staffEmail, fulfillmentId, step);
            if (step == last) {
                return;
            }
        }
    }

    // ================================================================== authorization

    @Test
    @DisplayName("BE04-01 queue lists only the Fulfillments of the Staff's own Store, with only that Store's items")
    void be04_01_queueIsScopedToTheStaffStore() {
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        data.markPaid(order);
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        long goVap = data.fulfillmentOf(order, "OS-GOVAP");

        List<FulfillmentQueueItemResponse> mine = queue(STAFF_THUDUC, FulfillmentQueueFilter.openWork());

        assertThat(mine).extracting(FulfillmentQueueItemResponse::fulfillmentId).contains(thuDuc).doesNotContain(goVap);
        assertThat(mine).allMatch(row -> row.storeId() == data.storeId("OS-THUDUC"));
        FulfillmentQueueItemResponse row = mine.stream().filter(r -> r.fulfillmentId() == thuDuc).findFirst().orElseThrow();
        assertThat(row.orderId()).isEqualTo(order);
        assertThat(row.itemCount()).isEqualTo(1);
        assertThat(detail(STAFF_THUDUC, thuDuc).items()).hasSize(1);
        assertThat(detail(STAFF_THUDUC, thuDuc).items().get(0).productName()).contains("Simple Purifying");
    }

    @Test
    @DisplayName("BE04-02 Staff of another Store is refused the detail of a Fulfillment (403, not 404)")
    void be04_02_otherStoreDetailIsForbidden() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);

        assertThatThrownBy(() -> detail(STAFF_GOVAP, fulfillment)).isInstanceOf(AccessDeniedException.class);
        assertThat(detail(STAFF_THUDUC, fulfillment).summary().fulfillmentId()).isEqualTo(fulfillment);
    }

    @Test
    @DisplayName("BE04-03 Staff of another Store cannot act on a Fulfillment: nothing is written")
    void be04_03_otherStoreCannotAct() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);
        int historyBefore = data.orderHistory(order);
        int fulfillmentHistoryBefore = data.fulfillmentHistory(fulfillment);

        assertThatThrownBy(() -> act(STAFF_GOVAP, fulfillment, FulfillmentAction.PREPARE))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("CONFIRMED");
        assertThat(data.orderStatus(order)).isEqualTo("CONFIRMED");
        assertThat(data.fulfillmentHistory(fulfillment)).isEqualTo(fulfillmentHistoryBefore);
        assertThat(data.orderHistory(order)).isEqualTo(historyBefore);
    }

    @Test
    @DisplayName("BE04-04 an unknown Fulfillment id is 404")
    void be04_04_unknownFulfillment() {
        assertThatThrownBy(() -> detail(STAFF_THUDUC, 999_999_999L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> act(STAFF_THUDUC, 999_999_999L, FulfillmentAction.PREPARE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("BE04-05 a customer principal cannot use the Staff service")
    void be04_05_customerIsRefused() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);

        assertThatThrownBy(() -> data.asRole(ALICE, "ROLE_CUSTOMER", () -> staff.getQueue(FulfillmentQueueFilter.none(), 0)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> data.asRole(ALICE, "ROLE_CUSTOMER",
                () -> staff.getFulfillment(fulfillment))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> data.asRole(ALICE, "ROLE_CUSTOMER", () -> {
            staff.perform(fulfillment, FulfillmentAction.PREPARE, null);
            return null;
        })).isInstanceOf(AccessDeniedException.class);
        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("BE04-06 Staff whose assignment is not ACTIVE sees and changes nothing")
    void be04_06_inactiveAssignment() {
        long order = data.codDelivery("OS-QUAN7", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);
        jdbc.update("update dbo.staff_store_assignments set status = 'INACTIVE' where user_id = 4");
        try {
            assertThatThrownBy(() -> queue(STAFF_QUAN7, FulfillmentQueueFilter.none()))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> act(STAFF_QUAN7, fulfillment, FulfillmentAction.PREPARE))
                    .isInstanceOf(AccessDeniedException.class);
        } finally {
            jdbc.update("update dbo.staff_store_assignments set status = 'ACTIVE' where user_id = 4");
        }
        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("BE04-07 the storeId filter narrows the assigned Stores and never widens them")
    void be04_07_storeFilterCannotWiden() {
        long order = data.codDelivery("OS-GOVAP", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);
        long goVap = data.storeId("OS-GOVAP");
        long thuDuc = data.storeId("OS-THUDUC");

        assertThatThrownBy(() -> queue(STAFF_THUDUC,
                new FulfillmentQueueFilter(goVap, null, null, null, null, null, false)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(queue(STAFF_THUDUC, new FulfillmentQueueFilter(thuDuc, null, null, null, null, null, false)))
                .allMatch(r -> r.storeId() == thuDuc).extracting(FulfillmentQueueItemResponse::fulfillmentId)
                .doesNotContain(fulfillment);
        assertThat(queue(STAFF_GOVAP, new FulfillmentQueueFilter(goVap, null, null, order, null, null, false)))
                .extracting(FulfillmentQueueItemResponse::fulfillmentId).containsExactly(fulfillment);
    }

    @Test
    @DisplayName("BE04-08 Staff assigned to two Stores sees the Fulfillments of both, and no third")
    void be04_08_twoAssignments() {
        long inGoVap = data.onlyFulfillment(data.codDelivery("OS-GOVAP", SERUM, 1));
        long inQuan7 = data.onlyFulfillment(data.codDelivery("OS-QUAN7", SHAMPOO, 1));
        jdbc.update("insert into dbo.staff_store_assignments (user_id, store_id, status) values (2, 2, 'ACTIVE')");
        try {
            List<Long> ids = queue(STAFF_THUDUC, FulfillmentQueueFilter.openWork()).stream()
                    .map(FulfillmentQueueItemResponse::fulfillmentId).toList();
            assertThat(ids).contains(inGoVap).doesNotContain(inQuan7);
            act(STAFF_THUDUC, inGoVap, FulfillmentAction.PREPARE);
            assertThat(data.fulfillmentStatus(inGoVap)).isEqualTo("PREPARING");
            assertThatThrownBy(() -> act(STAFF_THUDUC, inQuan7, FulfillmentAction.PREPARE))
                    .isInstanceOf(AccessDeniedException.class);
        } finally {
            jdbc.update("delete from dbo.staff_store_assignments where user_id = 2 and store_id = 2");
        }
    }

    @Test
    @DisplayName("BE04-09 orders.store_id does not decide authorization; the Fulfillment's Store does")
    void be04_09_ordersStoreIdIsNotAuthority() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);
        jdbc.update("update dbo.orders set store_id = ? where order_id = ?", data.storeId("OS-GOVAP"), order);

        assertThatThrownBy(() -> act(STAFF_GOVAP, fulfillment, FulfillmentAction.PREPARE))
                .isInstanceOf(AccessDeniedException.class);
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE);
        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("PREPARING");
    }

    @Test
    @DisplayName("BE04-10 the V2 order-level operations refuse an Order that has Fulfillments")
    void be04_10_legacyOrderLevelPathIsClosed() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);

        assertThatThrownBy(() -> data.asStaff(STAFF_THUDUC, () -> operations.performDelivery(order,
                com.oneshop.dto.response.DeliveryAction.PREPARE))).isInstanceOf(BadRequestException.class);

        assertThat(data.orderStatus(order)).isEqualTo("CONFIRMED");
        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("CONFIRMED");
    }

    // ================================================================== DELIVERY state machine

    @Test
    @DisplayName("BE04-11 COD delivery: CONFIRMED → PREPARING → PACKED → SHIPPING → COMPLETED, Order derived, COD collected once")
    void be04_11_codDeliveryHappyPath() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 2);
        long fulfillment = data.onlyFulfillment(order);
        int orderHistory = data.orderHistory(order);
        int fulfillmentHistory = data.fulfillmentHistory(fulfillment);
        assertThat(data.orderStatus(order)).isEqualTo("CONFIRMED");

        act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE);
        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("PREPARING");
        assertThat(data.orderStatus(order)).isEqualTo("PROCESSING");
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.PACK);
        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("PACKED");
        assertThat(data.orderStatus(order)).isEqualTo("PROCESSING");
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.SHIP);
        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("SHIPPING");
        assertThat(data.orderStatus(order)).isEqualTo("SHIPPING");
        assertThat(data.paymentStatus(order)).isEqualTo("UNPAID");
        assertThat(data.successfulPayments(order)).isZero();
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.COMPLETE_DELIVERY);

        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("COMPLETED");
        assertThat(data.orderStatus(order)).isEqualTo("COMPLETED");
        assertThat(data.paymentStatus(order)).isEqualTo("PAID");
        assertThat(data.successfulPayments(order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from dbo.payments p join dbo.orders o on o.order_id = "
                + "p.order_id where p.order_id = ? and p.status = 'SUCCESS' and p.amount = o.total_amount "
                + "and p.method = 'COD'", Integer.class, order)).isEqualTo(1);
        assertThat(data.fulfillmentHistory(fulfillment)).isEqualTo(fulfillmentHistory + 4);
        assertThat(data.orderHistory(order)).isEqualTo(orderHistory + 3);
        assertThat(jdbc.queryForObject("select completed_at from dbo.order_fulfillments where fulfillment_id = ?",
                Object.class, fulfillment)).isNotNull();
    }

    @Test
    @DisplayName("BE04-12 no step can be skipped")
    void be04_12_noSkipping() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);

        for (FulfillmentAction skipped : List.of(FulfillmentAction.PACK, FulfillmentAction.SHIP,
                FulfillmentAction.COMPLETE_DELIVERY, FulfillmentAction.FAIL_DELIVERY)) {
            FulfillmentConflictException ex = conflict(() -> act(STAFF_THUDUC, fulfillment, skipped, "lý do thử"));
            assertThat(ex.getCode()).isEqualTo(FulfillmentConflictException.STATUS_CHANGED);
        }
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE);
        assertThat(conflict(() -> act(STAFF_THUDUC, fulfillment, FulfillmentAction.SHIP)).getCode())
                .isEqualTo(FulfillmentConflictException.STATUS_CHANGED);
        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("PREPARING");
        assertThat(data.successfulPayments(order)).isZero();
    }

    @Test
    @DisplayName("BE04-13 a step cannot be repeated (double click) and writes one history row")
    void be04_13_noRepeat() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);
        int before = data.fulfillmentHistory(fulfillment);

        act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE);
        assertThat(conflict(() -> act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE)).getCode())
                .isEqualTo(FulfillmentConflictException.STATUS_CHANGED);

        assertThat(data.fulfillmentHistory(fulfillment)).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("BE04-14 pickup steps are refused on a DELIVERY Fulfillment and the other way round")
    void be04_14_wrongType() {
        long delivery = data.onlyFulfillment(data.codDelivery("OS-THUDUC", SERUM, 1));
        long pickup = data.onlyFulfillment(data.payAtStorePickup("OS-THUDUC", SHAMPOO, 1));
        act(STAFF_THUDUC, delivery, FulfillmentAction.PREPARE);
        act(STAFF_THUDUC, pickup, FulfillmentAction.PREPARE);

        assertThat(conflict(() -> act(STAFF_THUDUC, delivery, FulfillmentAction.MARK_READY)).getCode())
                .isEqualTo(FulfillmentConflictException.STATUS_CHANGED);
        assertThat(conflict(() -> act(STAFF_THUDUC, pickup, FulfillmentAction.PACK)).getCode())
                .isEqualTo(FulfillmentConflictException.STATUS_CHANGED);
        assertThat(data.fulfillmentStatus(delivery)).isEqualTo("PREPARING");
        assertThat(data.fulfillmentStatus(pickup)).isEqualTo("PREPARING");
    }

    @Test
    @DisplayName("BE04-15 tracking code is validated and stored at hand-over")
    void be04_15_trackingCode() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE);
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.PACK);

        assertThatThrownBy(() -> act(STAFF_THUDUC, fulfillment, FulfillmentAction.SHIP, "<script>")).isInstanceOf(BadRequestException.class);
        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("PACKED");

        act(STAFF_THUDUC, fulfillment, FulfillmentAction.SHIP, "GHN-123456");
        assertThat(detail(STAFF_THUDUC, fulfillment).trackingCode()).isEqualTo("GHN-123456");
    }

    @Test
    @DisplayName("BE04-16 COD is collected only when delivery is confirmed, and a rejected step collects nothing")
    void be04_16_codCollectedOnlyAtCompletion() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);
        driveDeliveryTo(STAFF_THUDUC, fulfillment, FulfillmentAction.SHIP);

        assertThat(data.paymentStatus(order)).isEqualTo("UNPAID");
        assertThat(detail(STAFF_THUDUC, fulfillment).amountToCollect()).isNotNull();
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.COMPLETE_DELIVERY);
        assertThat(data.successfulPayments(order)).isEqualTo(1);
        // a second completion cannot collect again
        assertThat(conflict(() -> act(STAFF_THUDUC, fulfillment, FulfillmentAction.COMPLETE_DELIVERY)).getCode())
                .isEqualTo(FulfillmentConflictException.STATUS_CHANGED);
        assertThat(data.successfulPayments(order)).isEqualTo(1);
    }

    @Test
    @DisplayName("BE04-17 a failure needs a written reason")
    void be04_17_failureNeedsReason() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);
        driveDeliveryTo(STAFF_THUDUC, fulfillment, FulfillmentAction.SHIP);

        for (String reason : new String[]{null, "", "   ", "abc"}) {
            assertThatThrownBy(() -> act(STAFF_THUDUC, fulfillment, FulfillmentAction.FAIL_DELIVERY, reason))
                    .isInstanceOf(BadRequestException.class);
        }
        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("SHIPPING");
        assertThat(data.orderStatus(order)).isEqualTo("SHIPPING");
    }

    @Test
    @DisplayName("BE04-18 FAILED moves the Order to NEEDS_ATTENTION only: no restock, no movement, no payment change")
    void be04_18_failedOnlyNeedsAttention() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 2);
        long fulfillment = data.onlyFulfillment(order);
        driveDeliveryTo(STAFF_THUDUC, fulfillment, FulfillmentAction.SHIP);
        Map<Long, List<Integer>> stock = data.stockSnapshot();
        long movements = data.movements();

        act(STAFF_THUDUC, fulfillment, FulfillmentAction.FAIL_DELIVERY, "Khách không nhận hàng, đã gọi 3 lần");

        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("FAILED");
        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION");
        assertThat(data.paymentStatus(order)).isEqualTo("UNPAID");
        assertThat(data.successfulPayments(order)).isZero();
        assertThat(data.stockSnapshot()).isEqualTo(stock);
        assertThat(data.movements()).isEqualTo(movements);
        assertThat(detail(STAFF_THUDUC, fulfillment).note()).contains("Khách không nhận hàng");
    }

    @Test
    @DisplayName("BE04-19 FAILED is terminal for Staff and a later step of another Store does not clear NEEDS_ATTENTION")
    void be04_19_failedIsTerminalAndNotCleared() {
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        data.markPaid(order);
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        long goVap = data.fulfillmentOf(order, "OS-GOVAP");
        driveDeliveryTo(STAFF_THUDUC, thuDuc, FulfillmentAction.SHIP);
        act(STAFF_THUDUC, thuDuc, FulfillmentAction.FAIL_DELIVERY, "Địa chỉ không tồn tại");
        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION");

        assertThat(conflict(() -> act(STAFF_THUDUC, thuDuc, FulfillmentAction.COMPLETE_DELIVERY)).getCode())
                .isEqualTo(FulfillmentConflictException.ORDER_NEEDS_ATTENTION);
        // the Order is held: the other Store cannot continue either, and the status is not cleared
        assertThat(conflict(() -> act(STAFF_GOVAP, goVap, FulfillmentAction.PREPARE)).getCode())
                .isEqualTo(FulfillmentConflictException.ORDER_NEEDS_ATTENTION);
        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION");
        assertThat(data.fulfillmentStatus(goVap)).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("BE04-20 every step writes the Fulfillment history with its actor; Order history only on change")
    void be04_20_history() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);
        int before = data.orderHistory(order);
        int fulfillmentBefore = data.fulfillmentHistory(fulfillment);

        act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE);
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.PACK);

        assertThat(data.fulfillmentHistory(fulfillment)).isEqualTo(fulfillmentBefore + 2);
        // CONFIRMED → PROCESSING is a change; PROCESSING stays PROCESSING for PACK: no Order history row
        assertThat(data.orderHistory(order)).isEqualTo(before + 1);
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_fulfillment_status_history h "
                + "join dbo.users u on u.user_id = h.changed_by_user_id where h.fulfillment_id = ? "
                + "and u.email = ?", Integer.class, fulfillment, STAFF_THUDUC)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_fulfillment_status_history "
                + "where fulfillment_id = ? and changed_by_user_id is null and old_status is null",
                Integer.class, fulfillment)).isEqualTo(1); // the creation row of the checkout
        assertThat(detail(STAFF_THUDUC, fulfillment).statusHistory()).extracting(h -> h.newStatus().name())
                .contains("PREPARING", "PACKED");
    }

    // ================================================================== STORE_PICKUP

    @Test
    @DisplayName("BE04-21 PAY_AT_STORE pickup: PREPARING → READY (code) → COMPLETED collects payment once")
    void be04_21_payAtStoreHappyPath() {
        long order = data.payAtStorePickup("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);

        act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE);
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.MARK_READY);
        String code = data.pickupCode(fulfillment);
        assertThat(code).matches("[A-HJ-NP-Z2-9]{8}");
        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("READY_FOR_PICKUP");
        assertThat(data.orderStatus(order)).isEqualTo("PROCESSING");
        assertThat(data.paymentStatus(order)).isEqualTo("UNPAID");

        act(STAFF_THUDUC, fulfillment, FulfillmentAction.COMPLETE_PICKUP, code);

        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("COMPLETED");
        assertThat(data.orderStatus(order)).isEqualTo("COMPLETED");
        assertThat(data.paymentStatus(order)).isEqualTo("PAID");
        assertThat(data.successfulPayments(order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_fulfillments where fulfillment_id = ? "
                + "and picked_up_at is not null and ready_at is not null", Integer.class, fulfillment)).isEqualTo(1);
    }

    @Test
    @DisplayName("BE04-22 a wrong pickup code changes nothing and collects nothing")
    void be04_22_wrongCode() {
        long order = data.payAtStorePickup("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE);
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.MARK_READY);
        int history = data.fulfillmentHistory(fulfillment);

        assertThatThrownBy(() -> act(STAFF_THUDUC, fulfillment, FulfillmentAction.COMPLETE_PICKUP, "ZZZZZZZZ"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> act(STAFF_THUDUC, fulfillment, FulfillmentAction.COMPLETE_PICKUP, null))
                .isInstanceOf(BadRequestException.class);

        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("READY_FOR_PICKUP");
        assertThat(data.paymentStatus(order)).isEqualTo("UNPAID");
        assertThat(data.successfulPayments(order)).isZero();
        assertThat(data.fulfillmentHistory(fulfillment)).isEqualTo(history);
    }

    @Test
    @DisplayName("BE04-23 the code is accepted regardless of case and surrounding spaces")
    void be04_23_codeNormalized() {
        long order = data.payAtStorePickup("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE);
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.MARK_READY);

        act(STAFF_THUDUC, fulfillment, FulfillmentAction.COMPLETE_PICKUP, "  " + data.pickupCode(fulfillment).toLowerCase() + " ");

        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("BE04-24 five wrong codes lock the code of that Fulfillment, even for the right code")
    void be04_24_attemptLimit() {
        long order = data.payAtStorePickup("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE);
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.MARK_READY);
        String code = data.pickupCode(fulfillment);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> act(STAFF_THUDUC, fulfillment, FulfillmentAction.COMPLETE_PICKUP, "AAAAAAAA"))
                    .isInstanceOf(BadRequestException.class);
        }

        assertThat(conflict(() -> act(STAFF_THUDUC, fulfillment, FulfillmentAction.COMPLETE_PICKUP, code)).getCode())
                .isEqualTo(FulfillmentConflictException.PICKUP_CODE_LOCKED);
        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("READY_FOR_PICKUP");
        assertThat(data.successfulPayments(order)).isZero();
    }

    @Test
    @DisplayName("BE04-25 the Staff views never contain the pickup code, only whether one was issued")
    void be04_25_staffNeverSeesTheCode() {
        long order = data.payAtStorePickup("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE);
        assertThat(detail(STAFF_THUDUC, fulfillment).pickupCodeIssued()).isFalse();
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.MARK_READY);
        String code = data.pickupCode(fulfillment);

        FulfillmentDetailResponse detail = detail(STAFF_THUDUC, fulfillment);

        assertThat(detail.pickupCodeIssued()).isTrue();
        assertThat(detail.toString()).doesNotContain(code);
        assertThat(queue(STAFF_THUDUC, FulfillmentQueueFilter.openWork()).toString()).doesNotContain(code);
        assertThat(jdbc.queryForList("select note from dbo.order_fulfillment_status_history where fulfillment_id = ?",
                String.class, fulfillment).toString()).doesNotContain(code);
    }

    @Test
    @DisplayName("BE04-26 the owner sees the code while goods wait at the Store; another customer sees nothing")
    void be04_26_customerSeesTheCode() {
        long order = data.payAtStorePickup("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE);
        OrderDetailResponse preparing = data.asRole(ALICE, "ROLE_CUSTOMER", () -> customerOrders.getOrder(ALICE, order));
        assertThat(preparing.fulfillments()).hasSize(1);
        assertThat(preparing.fulfillments().get(0).pickupCode()).isNull();

        act(STAFF_THUDUC, fulfillment, FulfillmentAction.MARK_READY);
        OrderDetailResponse ready = data.asRole(ALICE, "ROLE_CUSTOMER", () -> customerOrders.getOrder(ALICE, order));

        assertThat(ready.fulfillments().get(0).pickupCode()).isEqualTo(data.pickupCode(fulfillment));
        assertThat(ready.fulfillments().get(0).status()).isEqualTo(OrderFulfillmentStatus.READY_FOR_PICKUP);
        assertThatThrownBy(() -> data.asRole(BOB, "ROLE_CUSTOMER", () -> customerOrders.getOrder(BOB, order)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("BE04-27 ONLINE pickup that is already paid is completed with the code and creates no payment")
    void be04_27_onlinePickup() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);
        data.markPaid(order);
        long fulfillment = data.onlyFulfillment(order);
        int payments = data.successfulPayments(order);

        act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE);
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.MARK_READY);
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.COMPLETE_PICKUP, data.pickupCode(fulfillment));

        assertThat(data.orderStatus(order)).isEqualTo("COMPLETED");
        assertThat(data.successfulPayments(order)).isEqualTo(payments).isEqualTo(1);
    }

    @Test
    @DisplayName("BE04-28 READY needs PREPARING first; the pickup type cannot be packed or shipped")
    void be04_28_pickupOrder() {
        long fulfillment = data.onlyFulfillment(data.payAtStorePickup("OS-THUDUC", SERUM, 1));

        assertThat(conflict(() -> act(STAFF_THUDUC, fulfillment, FulfillmentAction.MARK_READY)).getCode())
                .isEqualTo(FulfillmentConflictException.STATUS_CHANGED);
        assertThat(conflict(() -> act(STAFF_THUDUC, fulfillment, FulfillmentAction.COMPLETE_PICKUP, "AAAAAAAA"))
                .getCode()).isEqualTo(FulfillmentConflictException.STATUS_CHANGED);
        assertThat(data.pickupCode(fulfillment)).isNull();
    }

    @Test
    @DisplayName("BE04-29 a pickup-only Store (no delivery) can still run its pickup Fulfillments")
    void be04_29_pickupOnlyStore() {
        long order = data.payAtStorePickup("OS-QUAN10", CHEEK, 1);
        long fulfillment = data.onlyFulfillment(order);

        act(STAFF_QUAN10, fulfillment, FulfillmentAction.PREPARE);
        act(STAFF_QUAN10, fulfillment, FulfillmentAction.MARK_READY);
        act(STAFF_QUAN10, fulfillment, FulfillmentAction.COMPLETE_PICKUP, data.pickupCode(fulfillment));

        assertThat(data.orderStatus(order)).isEqualTo("COMPLETED");
        assertThat(data.paymentStatus(order)).isEqualTo("PAID");
    }

    @Test
    @DisplayName("BE04-30 pickup codes of different Fulfillments are independent")
    void be04_30_codesAreIndependent() {
        long first = data.onlyFulfillment(data.payAtStorePickup("OS-THUDUC", SERUM, 1));
        long second = data.onlyFulfillment(data.payAtStorePickup("OS-THUDUC", SHAMPOO, 1));
        for (long id : new long[]{first, second}) {
            act(STAFF_THUDUC, id, FulfillmentAction.PREPARE);
            act(STAFF_THUDUC, id, FulfillmentAction.MARK_READY);
        }
        assertThat(data.pickupCode(first)).isNotEqualTo(data.pickupCode(second));

        assertThatThrownBy(() -> act(STAFF_THUDUC, first, FulfillmentAction.COMPLETE_PICKUP, data.pickupCode(second)))
                .isInstanceOf(BadRequestException.class);
        assertThat(data.fulfillmentStatus(first)).isEqualTo("READY_FOR_PICKUP");
    }

    // ================================================================== payment gate

    @Test
    @DisplayName("BE04-31 ONLINE PENDING_PAYMENT: no step, shown as blocked and counted as held, not as work")
    void be04_31_pendingPaymentIsBlocked() {
        StaffStoreDashboardResponse before = dashboard(STAFF_THUDUC, "OS-THUDUC");
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);

        assertThat(conflict(() -> act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE)).getCode())
                .isEqualTo(FulfillmentConflictException.PAYMENT_PENDING);
        FulfillmentQueueItemResponse row = queue(STAFF_THUDUC, FulfillmentQueueFilter.openWork()).stream()
                .filter(r -> r.fulfillmentId() == fulfillment).findFirst().orElseThrow();
        assertThat(row.blocked()).isTrue();
        assertThat(row.actions()).isEmpty();

        StaffStoreDashboardResponse after = dashboard(STAFF_THUDUC, "OS-THUDUC");
        assertThat(after.blockedCount()).isEqualTo(before.blockedCount() + 1);
        assertThat(after.openFulfillmentCount()).isEqualTo(before.openFulfillmentCount());
        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("CONFIRMED");
        assertThat(data.orderStatus(order)).isEqualTo("PENDING_PAYMENT");
    }

    private StaffStoreDashboardResponse dashboard(String email, String storeCode) {
        StaffDashboardResponse all = data.asStaff(email, () -> operations.getDashboard());
        long id = data.storeId(storeCode);
        return all.stores().stream().filter(s -> s.store().id() == id).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("BE04-32 an Order in NEEDS_ATTENTION is held and the aggregator does not clear it")
    void be04_32_needsAttentionIsHeld() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);
        data.setOrderStatus(order, "NEEDS_ATTENTION");

        assertThat(conflict(() -> act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE)).getCode())
                .isEqualTo(FulfillmentConflictException.ORDER_NEEDS_ATTENTION);
        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION");
        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("BE04-33 a CANCELLED Order cannot be worked on")
    void be04_33_cancelledIsHeld() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);
        data.asRole(ALICE, "ROLE_CUSTOMER", () -> {
            customerOrders.cancelOrder(ALICE, order);
            return null;
        });

        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("CANCELLED");
        assertThat(conflict(() -> act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE)).getCode())
                .isEqualTo(FulfillmentConflictException.ORDER_CANCELLED);
        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("BE04-34 ONLINE Order after a verified payment is workable")
    void be04_34_paidOnlineIsWorkable() {
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        data.markPaid(order);

        act(STAFF_THUDUC, data.fulfillmentOf(order, "OS-THUDUC"), FulfillmentAction.PREPARE);

        assertThat(data.fulfillmentStatus(data.fulfillmentOf(order, "OS-THUDUC"))).isEqualTo("PREPARING");
    }

    @Test
    @DisplayName("BE04-35 ONLINE not PAID although the Order says CONFIRMED is not workable")
    void be04_35_onlineNotPaidIsHeld() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);
        data.setOrderStatus(order, "CONFIRMED"); // inconsistent on purpose: payment_status is still UNPAID

        assertThat(conflict(() -> act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE)).getCode())
                .isEqualTo(FulfillmentConflictException.PAYMENT_NOT_VERIFIED);
        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("CONFIRMED");
    }

    // ================================================================== aggregation

    @Test
    @DisplayName("BE04-36 one Store PREPARING: Order PROCESSING, the other Fulfillment untouched (V3-T17)")
    void be04_36_oneStorePreparing() {
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        data.markPaid(order);
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        long goVap = data.fulfillmentOf(order, "OS-GOVAP");

        act(STAFF_THUDUC, thuDuc, FulfillmentAction.PREPARE);

        assertThat(data.orderStatus(order)).isEqualTo("PROCESSING");
        assertThat(data.fulfillmentStatus(thuDuc)).isEqualTo("PREPARING");
        assertThat(data.fulfillmentStatus(goVap)).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("BE04-37 one COMPLETED and one SHIPPING: PARTIALLY_COMPLETED (V3-T18); both COMPLETED: COMPLETED")
    void be04_37_partialThenComplete() {
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        data.markPaid(order);
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        long goVap = data.fulfillmentOf(order, "OS-GOVAP");

        driveDeliveryTo(STAFF_GOVAP, goVap, FulfillmentAction.SHIP);
        assertThat(data.orderStatus(order)).isEqualTo("SHIPPING");
        driveDeliveryTo(STAFF_THUDUC, thuDuc, FulfillmentAction.COMPLETE_DELIVERY);
        assertThat(data.orderStatus(order)).isEqualTo("PARTIALLY_COMPLETED");
        assertThat(data.fulfillmentStatus(goVap)).isEqualTo("SHIPPING");

        act(STAFF_GOVAP, goVap, FulfillmentAction.COMPLETE_DELIVERY);

        assertThat(data.orderStatus(order)).isEqualTo("COMPLETED");
        assertThat(data.paymentStatus(order)).isEqualTo("PAID");
        assertThat(data.successfulPayments(order)).isEqualTo(1);
    }

    @Test
    @DisplayName("BE04-38 FAILED beside a COMPLETED part: NEEDS_ATTENTION, the completed part is not reverted")
    void be04_38_failedBesideCompleted() {
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        data.markPaid(order);
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        long goVap = data.fulfillmentOf(order, "OS-GOVAP");
        driveDeliveryTo(STAFF_THUDUC, thuDuc, FulfillmentAction.COMPLETE_DELIVERY);
        driveDeliveryTo(STAFF_GOVAP, goVap, FulfillmentAction.SHIP);

        act(STAFF_GOVAP, goVap, FulfillmentAction.FAIL_DELIVERY, "Xe giao hàng gặp sự cố");

        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION");
        assertThat(data.fulfillmentStatus(thuDuc)).isEqualTo("COMPLETED");
        assertThat(data.fulfillmentStatus(goVap)).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("BE04-39 Order history only when the derived status changes")
    void be04_39_orderHistoryOnlyOnChange() {
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        data.markPaid(order);
        int before = data.orderHistory(order);

        act(STAFF_THUDUC, data.fulfillmentOf(order, "OS-THUDUC"), FulfillmentAction.PREPARE); // CONFIRMED → PROCESSING
        assertThat(data.orderHistory(order)).isEqualTo(before + 1);
        act(STAFF_GOVAP, data.fulfillmentOf(order, "OS-GOVAP"), FulfillmentAction.PREPARE); // stays PROCESSING

        assertThat(data.orderStatus(order)).isEqualTo("PROCESSING");
        assertThat(data.orderHistory(order)).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("BE04-40 no Staff method accepts an Order status")
    void be04_40_noOrderStatusInput() {
        for (Method method : StaffFulfillmentService.class.getMethods()) {
            assertThat(List.of(method.getParameterTypes())).as(method.getName())
                    .doesNotContain(com.oneshop.entity.OrderStatus.class);
        }
        assertThat(List.of(FulfillmentAction.values())).extracting(FulfillmentAction::getTo)
                .doesNotContain(OrderFulfillmentStatus.CANCELLED);
    }

    // ================================================================== inventory

    @Test
    @DisplayName("BE04-41 a whole delivery lifecycle changes neither stock nor movements")
    void be04_41_noStockChangeInLifecycle() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 3);
        long fulfillment = data.onlyFulfillment(order);
        Map<Long, List<Integer>> stock = data.stockSnapshot();
        long movements = data.movements();

        driveDeliveryTo(STAFF_THUDUC, fulfillment, FulfillmentAction.COMPLETE_DELIVERY);

        assertThat(data.stockSnapshot()).isEqualTo(stock);
        assertThat(data.movements()).isEqualTo(movements);
        assertThat(jdbc.queryForObject("select count(*) from dbo.inventory_movements where reference_order_id = ? "
                + "and type not in ('ORDER')", Integer.class, order)).isZero();
    }

    @Test
    @DisplayName("BE04-42 a whole pickup lifecycle changes neither stock nor movements")
    void be04_42_noStockChangeInPickup() {
        long order = data.payAtStorePickup("OS-THUDUC", SHAMPOO, 2);
        long fulfillment = data.onlyFulfillment(order);
        Map<Long, List<Integer>> stock = data.stockSnapshot();
        long movements = data.movements();

        act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE);
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.MARK_READY);
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.COMPLETE_PICKUP, data.pickupCode(fulfillment));

        assertThat(data.stockSnapshot()).isEqualTo(stock);
        assertThat(data.movements()).isEqualTo(movements);
    }

    @Test
    @DisplayName("BE04-43 the customer cannot cancel once a Store has started: conflict, nothing restored")
    void be04_43_cancelAfterPreparingIsRefused() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 2);
        long fulfillment = data.onlyFulfillment(order);
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.PREPARE);
        Map<Long, List<Integer>> stock = data.stockSnapshot();
        long movements = data.movements();

        FulfillmentConflictException ex = conflict(() -> data.asRole(ALICE, "ROLE_CUSTOMER", () -> {
            customerOrders.cancelOrder(ALICE, order);
            return null;
        }));

        assertThat(ex.getCode()).isEqualTo(FulfillmentConflictException.ORDER_ALREADY_PREPARING);
        assertThat(data.orderStatus(order)).isEqualTo("PROCESSING");
        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("PREPARING");
        assertThat(data.stockSnapshot()).isEqualTo(stock);
        assertThat(data.movements()).isEqualTo(movements);
    }

    @Test
    @DisplayName("BE04-44 cancelling before any step restores stock once and closes the Fulfillment")
    void be04_44_cancelBeforePreparing() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 2);
        long fulfillment = data.onlyFulfillment(order);
        long movements = data.movements();

        data.asRole(ALICE, "ROLE_CUSTOMER", () -> {
            customerOrders.cancelOrder(ALICE, order);
            return null;
        });
        data.asRole(ALICE, "ROLE_CUSTOMER", () -> {
            customerOrders.cancelOrder(ALICE, order); // idempotent
            return null;
        });

        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("CANCELLED");
        assertThat(data.movements()).isEqualTo(movements + 1);
        assertThat(jdbc.queryForObject("select count(*) from dbo.inventory_movements where reference_order_id = ? "
                + "and type = 'CANCEL_ORDER'", Integer.class, order)).isEqualTo(1);
    }

    @Test
    @DisplayName("BE04-68 the customer cannot cancel an Order that waits for an administrator (NEEDS_ATTENTION)")
    void be04_68_cancelNeedsAttentionIsRefused() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 2);
        long fulfillment = data.onlyFulfillment(order);
        driveDeliveryTo(STAFF_THUDUC, fulfillment, FulfillmentAction.SHIP);
        act(STAFF_THUDUC, fulfillment, FulfillmentAction.FAIL_DELIVERY, "Khách không nhận hàng");
        Map<Long, List<Integer>> stock = data.stockSnapshot();
        long movements = data.movements();

        FulfillmentConflictException ex = conflict(() -> data.asRole(ALICE, "ROLE_CUSTOMER", () -> {
            customerOrders.cancelOrder(ALICE, order);
            return null;
        }));

        assertThat(ex.getCode()).isEqualTo(FulfillmentConflictException.ORDER_NEEDS_ATTENTION);
        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION");
        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("FAILED");
        assertThat(data.stockSnapshot()).isEqualTo(stock);
        assertThat(data.movements()).isEqualTo(movements);
    }

    // ================================================================== queue and dashboard

    @Test
    @DisplayName("BE04-45 queue filters by status and type and lists open work before finished work")
    void be04_45_queueFilters() {
        long deliveryOrder = data.codDelivery("OS-THUDUC", SERUM, 1);
        long pickupOrder = data.payAtStorePickup("OS-THUDUC", SHAMPOO, 1);
        long delivery = data.onlyFulfillment(deliveryOrder);
        long pickup = data.onlyFulfillment(pickupOrder);
        driveDeliveryTo(STAFF_THUDUC, delivery, FulfillmentAction.COMPLETE_DELIVERY);

        List<Long> deliveryOnly = queue(STAFF_THUDUC, new FulfillmentQueueFilter(null, null,
                FulfillmentType.DELIVERY, null, null, null, false)).stream()
                .map(FulfillmentQueueItemResponse::fulfillmentId).toList();
        List<Long> pickupOnly = queue(STAFF_THUDUC, new FulfillmentQueueFilter(null, null,
                FulfillmentType.STORE_PICKUP, null, null, null, true)).stream()
                .map(FulfillmentQueueItemResponse::fulfillmentId).toList();
        List<Long> completed = queue(STAFF_THUDUC, new FulfillmentQueueFilter(null,
                OrderFulfillmentStatus.COMPLETED, null, null, null, null, false)).stream()
                .map(FulfillmentQueueItemResponse::fulfillmentId).toList();
        List<Long> open = queue(STAFF_THUDUC, FulfillmentQueueFilter.openWork()).stream()
                .map(FulfillmentQueueItemResponse::fulfillmentId).toList();

        assertThat(deliveryOnly).contains(delivery).doesNotContain(pickup);
        assertThat(pickupOnly).contains(pickup).doesNotContain(delivery);
        assertThat(completed).contains(delivery);
        assertThat(open).contains(pickup).doesNotContain(delivery);
    }

    @Test
    @DisplayName("BE04-46 the dashboard counts Fulfillments per assigned Store from SQL")
    void be04_46_dashboardCounts() {
        StaffStoreDashboardResponse before = dashboard(STAFF_THUDUC, "OS-THUDUC");
        long pickup = data.onlyFulfillment(data.payAtStorePickup("OS-THUDUC", SERUM, 1));
        long delivery = data.onlyFulfillment(data.codDelivery("OS-THUDUC", SHAMPOO, 1));

        StaffStoreDashboardResponse created = dashboard(STAFF_THUDUC, "OS-THUDUC");
        assertThat(created.openFulfillmentCount()).isEqualTo(before.openFulfillmentCount() + 2);

        act(STAFF_THUDUC, pickup, FulfillmentAction.PREPARE);
        act(STAFF_THUDUC, pickup, FulfillmentAction.MARK_READY);
        driveDeliveryTo(STAFF_THUDUC, delivery, FulfillmentAction.SHIP);

        StaffStoreDashboardResponse working = dashboard(STAFF_THUDUC, "OS-THUDUC");
        assertThat(working.waitingPickupCount()).isEqualTo(before.waitingPickupCount() + 1);
        assertThat(working.shippingCount()).isEqualTo(before.shippingCount() + 1);
        assertThat(working.openFulfillmentCount()).isEqualTo(before.openFulfillmentCount() + 2);

        act(STAFF_THUDUC, delivery, FulfillmentAction.FAIL_DELIVERY, "Khách từ chối nhận hàng");
        StaffStoreDashboardResponse failed = dashboard(STAFF_THUDUC, "OS-THUDUC");
        assertThat(failed.failedCount()).isEqualTo(before.failedCount() + 1);
        assertThat(failed.shippingCount()).isEqualTo(before.shippingCount());
    }

    @Test
    @DisplayName("BE04-47 the dashboard of one Store ignores another Store's Fulfillments")
    void be04_47_dashboardIsScoped() {
        StaffStoreDashboardResponse before = dashboard(STAFF_GOVAP, "OS-GOVAP");
        data.codDelivery("OS-THUDUC", SERUM, 1);

        assertThat(dashboard(STAFF_GOVAP, "OS-GOVAP").openFulfillmentCount()).isEqualTo(before.openFulfillmentCount());
        assertThat(data.asStaff(STAFF_GOVAP, () -> operations.getDashboard()).stores()).hasSize(1);
    }

    @Test
    @DisplayName("BE04-48 the detail of a Fulfillment lists only its own lines when the Order has two Stores")
    void be04_48_detailOnlyOwnLines() {
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        data.markPaid(order);

        FulfillmentDetailResponse thuDuc = detail(STAFF_THUDUC, data.fulfillmentOf(order, "OS-THUDUC"));
        FulfillmentDetailResponse goVap = detail(STAFF_GOVAP, data.fulfillmentOf(order, "OS-GOVAP"));

        assertThat(thuDuc.items()).hasSize(1);
        assertThat(goVap.items()).hasSize(1);
        assertThat(thuDuc.items().get(0).productName()).isNotEqualTo(goVap.items().get(0).productName());
        assertThat(thuDuc.amountToCollect()).isNull();
        assertThat(thuDuc.summary().orderId()).isEqualTo(goVap.summary().orderId());
    }
}
