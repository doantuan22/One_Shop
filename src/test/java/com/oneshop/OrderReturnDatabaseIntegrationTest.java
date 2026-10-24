package com.oneshop;

import com.oneshop.dto.response.ImageUploadResult;
import com.oneshop.dto.response.OrderReturnResponse;
import com.oneshop.dto.response.StaffReturnItemResponse;
import com.oneshop.entity.OrderReturnStatus;
import com.oneshop.entity.ReturnDisposition;
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
import com.oneshop.service.CloudinaryService;
import com.oneshop.service.fulfillment.FulfillmentAction;
import com.oneshop.service.fulfillment.StaffFulfillmentService;
import com.oneshop.service.payment.OrderRefundViewService;
import com.oneshop.service.payment.RefundSimulator;
import com.oneshop.service.returns.AdminOrderReturnService;
import com.oneshop.service.returns.CustomerOrderReturnService;
import com.oneshop.service.returns.StaffOrderReturnService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Post-delivery return on the real SQL Server: the whole completed Order is returned within 3 days of the latest
 * Fulfillment completion, an Administrator (never Staff) decides, the goods (not the shipping fee) are refunded in
 * simulation at approval, and the Staff of each delivering Store restock or dispose of their own lines once. These tests
 * commit; {@link SeedGuardV3} puts the test database back, so they only run on a {@code *_test} database.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=test-only-secret-test-only-secret-1234567890",
        "oneshop.checkout.online-enabled=true",
        "oneshop.payment.timeout-worker-enabled=false"})
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class OrderReturnDatabaseIntegrationTest {

    static final String ADMIN = "admin@oneshop.vn";
    static final String ALICE = "khachhang1@example.com";
    static final String BOB = "khachhang2@example.com";
    static final String STAFF_THUDUC = "staff.thuduc@oneshop.vn";
    static final String STAFF_GOVAP = "staff.govap@oneshop.vn";
    static final String SERUM = "SIMPLE-PURIFY-150";
    static final String SHAMPOO = "VASELINE-LIP-ORIG-48";

    @Autowired CustomerOrderReturnService customer;
    @Autowired AdminOrderReturnService admin;
    @Autowired StaffOrderReturnService staffReturns;
    @Autowired StaffFulfillmentService staff;
    @Autowired OrderRefundViewService refundView;
    @MockitoBean CloudinaryService cloudinary;
    @MockitoSpyBean RefundSimulator refundSimulator;
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

    @BeforeEach
    void remember() {
        guard = new SeedGuardV3(jdbc);
        assumeTrue(guard.isTestDatabase(), "these tests commit: run with DB_NAME=oneshop_test");
        guard.remember();
        remembered = true;
        data = new FulfillmentFixtures(jdbc, cartService, checkoutService, cartRepository, cartItemRepository,
                productRepository, storeRepository, storeProductRepository);
        when(cloudinary.uploadImage(any(), anyString())).thenReturn(
                new ImageUploadResult("https://res.cloudinary.com/demo/image/upload/v1/returns/proof.jpg", "returns/proof"));
    }

    @AfterEach
    void restore() {
        reset(refundSimulator);
        if (remembered) {
            guard.restore();
        }
    }

    // ------------------------------------------------------------------ helpers

    private <T> T asCustomer(String email, Supplier<T> action) {
        return data.asRole(email, "ROLE_CUSTOMER", action);
    }

    private <T> T asAdmin(Supplier<T> action) {
        return data.asRole(ADMIN, "ROLE_ADMIN", action);
    }

    private void act(String staffEmail, long fulfillment, FulfillmentAction action, String input) {
        data.asStaff(staffEmail, () -> staff.perform(fulfillment, action, input));
    }

    private void completeDelivery(String staffEmail, long part) {
        act(staffEmail, part, FulfillmentAction.PREPARE, null);
        act(staffEmail, part, FulfillmentAction.PACK, null);
        act(staffEmail, part, FulfillmentAction.SHIP, "TRK-" + part);
        act(staffEmail, part, FulfillmentAction.COMPLETE_DELIVERY, null);
    }

    /** An ONLINE paid Order of two Stores, both parts delivered: COMPLETED, completed just now. */
    private long deliveredTwoStores() {
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        data.markPaid(order);
        jdbc.update("update dbo.payments set refund_status = 'NONE' where order_id = ?", order);
        completeDelivery(STAFF_THUDUC, data.fulfillmentOf(order, "OS-THUDUC"));
        completeDelivery(STAFF_GOVAP, data.fulfillmentOf(order, "OS-GOVAP"));
        assertThat(data.orderStatus(order)).isEqualTo("COMPLETED");
        return order;
    }

    private void completedAgo(long fulfillment, String unit, int amount) {
        jdbc.update("update dbo.order_fulfillments set completed_at = dateadd(" + unit + ", -?, sysdatetime()) "
                + "where fulfillment_id = ?", amount, fulfillment);
    }

    private static MultipartFile photo() {
        return new MockMultipartFile("images", "proof.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    private OrderReturnResponse request(String email, long order, String reason) {
        return asCustomer(email, () -> customer.request(email, order, reason, List.of(photo())));
    }

    private BigDecimal money(String sql, Object... args) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal goods(long order) {
        return money("select coalesce(sum(subtotal), 0) from dbo.order_items where order_id = ?", order);
    }

    private BigDecimal fee(long order) {
        return money("select shipping_fee from dbo.orders where order_id = ?", order);
    }

    private int ledger(long order, String component) {
        return jdbc.queryForObject("select count(*) from dbo.payment_refunds where order_id = ? and component = ?",
                Integer.class, order, component);
    }

    private int quantityOf(long returnItem) {
        return jdbc.queryForObject("select sp.quantity from dbo.order_return_items i join dbo.store_products sp on "
                + "sp.store_product_id = i.store_product_id where i.return_item_id = ?", Integer.class, returnItem);
    }

    private long itemOfStore(long returnId, String storeCode) {
        return jdbc.queryForObject("select i.return_item_id from dbo.order_return_items i join dbo.stores s on "
                + "s.store_id = i.store_id where i.return_id = ? and s.code = ?", Long.class, returnId, storeCode);
    }

    // ================================================================== request

    @Test
    @DisplayName("RET-01 the customer asks to return the whole Order: all delivered lines are copied, the refund is the goods only, nothing else moves")
    void requestCopiesTheWholeOrder() {
        long order = deliveredTwoStores();
        var eligibility = asCustomer(ALICE, () -> customer.eligibility(ALICE, order));
        assertThat(eligibility.allowed()).isTrue();
        assertThat(eligibility.refundAmount()).isEqualByComparingTo(goods(order));

        OrderReturnResponse made = request(ALICE, order, "Sản phẩm bị lỗi khi mở hộp");

        assertThat(made.status()).isEqualTo(OrderReturnStatus.REQUESTED);
        assertThat(made.items()).hasSize(2);
        assertThat(made.imageUrls()).hasSize(1);
        assertThat(made.refundAmount()).isEqualByComparingTo(goods(order)).isLessThan(goods(order).add(fee(order)));
        assertThat(made.reviewerName()).as("the customer never sees staff names").isNull();
        assertThat(data.orderStatus(order)).as("a return is a layer above the Order").isEqualTo("COMPLETED");
        assertThat(data.fulfillments(order)).allMatch(f -> f.status().equals("COMPLETED"));
        assertThat(ledger(order, "RETURN")).as("nothing is refunded before an Administrator approves").isZero();
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_return_items where return_id = ? "
                + "and disposition = 'PENDING'", Integer.class, made.returnId())).isEqualTo(2);
        // Staff see nothing until the Administrator approves
        assertThat(data.asStaff(STAFF_THUDUC, () -> staffReturns.list(ReturnDisposition.PENDING, 0)).getContent()
                .stream().filter(l -> l.orderId().equals(order))).isEmpty();
    }

    @Test
    @DisplayName("RET-02 one request per Order; another customer cannot see or return it")
    void oneRequestPerOrderAndOwnerOnly() {
        long order = deliveredTwoStores();
        request(ALICE, order, "Giao sai sản phẩm");

        assertThatThrownBy(() -> request(ALICE, order, "Gửi lại lần hai"))
                .isInstanceOf(BadRequestException.class);
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_returns where order_id = ?", Integer.class, order)).isEqualTo(1);
        assertThatThrownBy(() -> request(BOB, order, "Đơn của người khác")).isInstanceOf(ResourceNotFoundException.class);
        assertThat(asCustomer(BOB, () -> customer.find(BOB, order))).isEmpty();
    }

    @Test
    @DisplayName("RET-03 reason and photos are required; a bad request stores nothing")
    void reasonAndPhotosRequired() {
        long order = deliveredTwoStores();

        assertThatThrownBy(() -> asCustomer(ALICE, () -> customer.request(ALICE, order, "  ", List.of(photo()))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> asCustomer(ALICE, () -> customer.request(ALICE, order, "Sản phẩm bị lỗi", List.of())))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> asCustomer(ALICE, () -> customer.request(ALICE, order, "Sản phẩm bị lỗi",
                List.of(photo(), photo(), photo(), photo())))).isInstanceOf(BadRequestException.class);
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_returns where order_id = ?", Integer.class, order)).isZero();
    }

    @Test
    @DisplayName("RET-04 the 3 days start at the LATEST Fulfillment completion: 2 days ago is allowed, 4 days ago is refused; a part finished long ago does not matter")
    void threeDayWindowFromTheLatestCompletion() {
        long order = deliveredTwoStores();
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        long goVap = data.fulfillmentOf(order, "OS-GOVAP");
        completedAgo(thuDuc, "day", 10);
        completedAgo(goVap, "day", 2);
        assertThat(asCustomer(ALICE, () -> customer.eligibility(ALICE, order)).allowed())
                .as("the later part decides").isTrue();

        completedAgo(goVap, "day", 4);
        var blocked = asCustomer(ALICE, () -> customer.eligibility(ALICE, order));
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.message()).contains("3 ngày");
        assertThatThrownBy(() -> request(ALICE, order, "Quá hạn rồi nhưng vẫn gửi"))
                .isInstanceOf(BadRequestException.class);
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_returns where order_id = ?", Integer.class, order)).isZero();
    }

    @Test
    @DisplayName("RET-05 an Order that is not COMPLETED cannot be returned")
    void notCompletedOrderIsRefused() {
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        data.markPaid(order);
        var eligibility = asCustomer(ALICE, () -> customer.eligibility(ALICE, order));
        assertThat(eligibility.allowed()).isFalse();
        assertThatThrownBy(() -> request(ALICE, order, "Chưa nhận hàng mà đã trả")).isInstanceOf(BadRequestException.class);
    }

    // ================================================================== admin

    @Test
    @DisplayName("RET-06 approval refunds the goods at once (shipping kept) in one RETURN ledger row, only once")
    void approvalRefundsTheGoodsOnce() {
        long order = deliveredTwoStores();
        OrderReturnResponse made = request(ALICE, order, "Sản phẩm bị lỗi khi mở hộp");
        BigDecimal collected = money("select amount from dbo.payments where order_id = ? and status = 'SUCCESS'", order);

        OrderReturnResponse approved = asAdmin(() -> admin.approve(made.returnId(), ADMIN));

        assertThat(approved.status()).isEqualTo(OrderReturnStatus.APPROVED);
        assertThat(approved.reviewerName()).isNotNull();
        var row = jdbc.queryForMap("select * from dbo.payment_refunds where order_id = ? and component = 'RETURN'", order);
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo(goods(order));
        assertThat(row.get("status")).isEqualTo("SIMULATED_REFUNDED");
        assertThat(((Number) row.get("return_id")).longValue()).isEqualTo(made.returnId());
        assertThat(((Number) row.get("requested_by_user_id")).longValue()).isEqualTo(
                jdbc.queryForObject("select user_id from dbo.users where email = ?", Long.class, ADMIN));
        assertThat(collected.subtract(goods(order))).as("the shipping fee stays with the shop").isEqualByComparingTo(fee(order));
        assertThat(data.paymentStatus(order)).as("only part of the money went back").isEqualTo("PAID");
        assertThat(data.orderStatus(order)).isEqualTo("COMPLETED");
        var summary = refundView.forCustomer(ALICE, order);
        assertThat(summary.refunded()).isEqualByComparingTo(goods(order));
        assertThat(summary.lines().get(0).label()).contains("trả lại");

        assertThatThrownBy(() -> asAdmin(() -> admin.approve(made.returnId(), ADMIN)))
                .isInstanceOfSatisfying(FulfillmentConflictException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(FulfillmentConflictException.RETURN_ALREADY_DECIDED));
        assertThat(ledger(order, "RETURN")).as("a repeated approval refunds nothing more").isEqualTo(1);
    }

    @Test
    @DisplayName("RET-07 a failed refund simulation rolls the approval back whole; repeating it works")
    void failedSimulationRollsBack() {
        long order = deliveredTwoStores();
        OrderReturnResponse made = request(ALICE, order, "Sản phẩm bị lỗi khi mở hộp");
        doThrow(new IllegalStateException("bank down")).when(refundSimulator).simulate(any());

        assertThatThrownBy(() -> asAdmin(() -> admin.approve(made.returnId(), ADMIN)))
                .isInstanceOf(FulfillmentConflictException.class);

        assertThat(asAdmin(() -> admin.get(made.returnId())).status()).isEqualTo(OrderReturnStatus.REQUESTED);
        assertThat(ledger(order, "RETURN")).isZero();
        reset(refundSimulator);
        assertThat(asAdmin(() -> admin.approve(made.returnId(), ADMIN)).status()).isEqualTo(OrderReturnStatus.APPROVED);
        assertThat(ledger(order, "RETURN")).isEqualTo(1);
    }

    @Test
    @DisplayName("RET-08 rejection needs a reason, is final, moves no money and shows nothing to Staff")
    void rejectionIsFinal() {
        long order = deliveredTwoStores();
        OrderReturnResponse made = request(ALICE, order, "Sản phẩm bị lỗi khi mở hộp");

        assertThatThrownBy(() -> asAdmin(() -> admin.reject(made.returnId(), " ", ADMIN)))
                .isInstanceOf(BadRequestException.class);
        OrderReturnResponse rejected = asAdmin(() -> admin.reject(made.returnId(), "Ảnh không thể hiện lỗi của sản phẩm", ADMIN));

        assertThat(rejected.status()).isEqualTo(OrderReturnStatus.REJECTED);
        assertThat(rejected.reviewNote()).contains("Ảnh không thể hiện");
        assertThat(ledger(order, "RETURN")).isZero();
        assertThatThrownBy(() -> asAdmin(() -> admin.approve(made.returnId(), ADMIN))).isInstanceOf(FulfillmentConflictException.class);
        assertThatThrownBy(() -> request(ALICE, order, "Gửi lại sau khi bị từ chối")).isInstanceOf(BadRequestException.class);
        assertThat(data.asStaff(STAFF_THUDUC, () -> staffReturns.list(null, 0)).getContent().stream()
                .filter(l -> l.orderId().equals(order))).isEmpty();
        assertThat(asCustomer(ALICE, () -> customer.find(ALICE, order)).orElseThrow().status()).isEqualTo(OrderReturnStatus.REJECTED);
    }

    @Test
    @DisplayName("RET-09 only an Administrator decides: Staff and customers are refused")
    void staffAndCustomersCannotDecide() {
        long order = deliveredTwoStores();
        OrderReturnResponse made = request(ALICE, order, "Sản phẩm bị lỗi khi mở hộp");

        assertThatThrownBy(() -> data.asStaff(STAFF_THUDUC, () -> admin.approve(made.returnId(), STAFF_THUDUC)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> asCustomer(ALICE, () -> admin.approve(made.returnId(), ALICE)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(ledger(order, "RETURN")).isZero();
    }

    // ================================================================== staff

    @Test
    @DisplayName("RET-10 after approval each Store's Staff see only their own lines; restock adds the exact quantity, dispose changes nothing, each once")
    void staffRestockOrDisposeOwnLinesOnce() {
        long order = deliveredTwoStores();
        OrderReturnResponse made = request(ALICE, order, "Sản phẩm bị lỗi khi mở hộp");
        asAdmin(() -> admin.approve(made.returnId(), ADMIN));
        long thuDucLine = itemOfStore(made.returnId(), "OS-THUDUC");
        long goVapLine = itemOfStore(made.returnId(), "OS-GOVAP");

        List<StaffReturnItemResponse> thuDuc = data.asStaff(STAFF_THUDUC, () -> staffReturns.list(ReturnDisposition.PENDING, 0)).getContent()
                .stream().filter(l -> l.orderId().equals(order)).toList();
        assertThat(thuDuc).extracting(StaffReturnItemResponse::returnItemId).containsExactly(thuDucLine);

        int before = quantityOf(thuDucLine);
        long movements = data.movements();
        data.asStaff(STAFF_THUDUC, () -> staffReturns.handle(thuDucLine, ReturnDisposition.RESTOCKED, "Hàng còn nguyên seal", STAFF_THUDUC));
        assertThat(quantityOf(thuDucLine)).isEqualTo(before + 1);
        var movement = jdbc.queryForMap("select top 1 * from dbo.inventory_movements where type = 'RETURN_ORDER' "
                + "and reference_order_id = ? order by movement_id desc", order);
        assertThat(((Number) movement.get("quantity_change")).intValue()).isEqualTo(1);
        assertThat(((Number) movement.get("quantity_after")).intValue()).isEqualTo(before + 1);
        assertThat(movement.get("staff_id")).isNotNull();
        assertThat(data.movements()).isEqualTo(movements + 1);

        assertThatThrownBy(() -> data.asStaff(STAFF_THUDUC, () -> staffReturns.handle(thuDucLine, ReturnDisposition.RESTOCKED, null, STAFF_THUDUC)))
                .isInstanceOfSatisfying(FulfillmentConflictException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(FulfillmentConflictException.RETURN_ALREADY_HANDLED));
        assertThat(quantityOf(thuDucLine)).as("a repeated restock adds nothing").isEqualTo(before + 1);
        assertThatThrownBy(() -> data.asStaff(STAFF_THUDUC, () -> staffReturns.handle(goVapLine, ReturnDisposition.DISPOSED, null, STAFF_THUDUC)))
                .as("a Store's Staff cannot touch another Store's line").isInstanceOf(AccessDeniedException.class);
        assertThat(asAdmin(() -> admin.get(made.returnId())).status()).isEqualTo(OrderReturnStatus.APPROVED);

        int goVapBefore = quantityOf(goVapLine);
        long movementsBefore = data.movements();
        data.asStaff(STAFF_GOVAP, () -> staffReturns.handle(goVapLine, ReturnDisposition.DISPOSED, "Hộp móp, xử lý theo quy định", STAFF_GOVAP));
        assertThat(quantityOf(goVapLine)).as("disposal never touches stock").isEqualTo(goVapBefore);
        assertThat(data.movements()).isEqualTo(movementsBefore);

        OrderReturnResponse done = asAdmin(() -> admin.get(made.returnId()));
        assertThat(done.status()).isEqualTo(OrderReturnStatus.COMPLETED);
        assertThat(done.completedAt()).isNotNull();
        assertThat(done.items()).extracting(OrderReturnResponse.Item::disposition)
                .containsExactlyInAnyOrder(ReturnDisposition.RESTOCKED, ReturnDisposition.DISPOSED);
        assertThat(ledger(order, "RETURN")).as("handling goods never moves money").isEqualTo(1);
        assertThat(data.orderStatus(order)).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("RET-11 a pickup Order paid at the Store (no shipping fee) is refunded in full and the Payment becomes REFUNDED")
    void payAtStorePickupIsRefundedInFull() {
        long order = data.payAtStorePickup("OS-THUDUC", SERUM, 1);
        long part = data.onlyFulfillment(order);
        act(STAFF_THUDUC, part, FulfillmentAction.PREPARE, null);
        act(STAFF_THUDUC, part, FulfillmentAction.MARK_READY, null);
        String code = data.pickupCode(part);
        act(STAFF_THUDUC, part, FulfillmentAction.COMPLETE_PICKUP, code);
        assertThat(data.orderStatus(order)).isEqualTo("COMPLETED");

        OrderReturnResponse made = request(ALICE, order, "Không đúng loại da của tôi");
        asAdmin(() -> admin.approve(made.returnId(), ADMIN));

        assertThat(ledger(order, "RETURN")).isEqualTo(1);
        assertThat(money("select amount from dbo.payment_refunds where order_id = ?", order)).isEqualByComparingTo(goods(order));
        assertThat(fee(order)).isEqualByComparingTo("0");
        assertThat(data.paymentStatus(order)).as("everything collected went back").isEqualTo("REFUNDED");
    }
}
