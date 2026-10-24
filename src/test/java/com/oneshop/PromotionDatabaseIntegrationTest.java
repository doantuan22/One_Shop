package com.oneshop;

import com.oneshop.dto.request.AddCartItemRequest;
import com.oneshop.dto.request.PlaceOrderRequest;
import com.oneshop.dto.request.PromotionRequest;
import com.oneshop.dto.response.CheckoutPreviewResponse;
import com.oneshop.dto.response.CheckoutResultResponse;
import com.oneshop.dto.response.OrderReturnResponse;
import com.oneshop.dto.response.PromotionResponse;
import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.PromotionStatus;
import com.oneshop.entity.PromotionType;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.CheckoutConflictException;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.CartService;
import com.oneshop.service.CheckoutService;
import com.oneshop.service.CloudinaryService;
import com.oneshop.service.CustomerOrderService;
import com.oneshop.service.fulfillment.FulfillmentAction;
import com.oneshop.service.fulfillment.StaffFulfillmentService;
import com.oneshop.service.promotion.AdminPromotionService;
import com.oneshop.service.returns.AdminOrderReturnService;
import com.oneshop.service.returns.CustomerOrderReturnService;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Promotion codes on the real SQL Server: the discount comes off the goods only, the total the customer pays and every
 * Payment follow it, the conditions (minimum goods, dates, status, uses left, uses per customer) are checked again when
 * the order is placed, the last use cannot be spent twice, a cancelled checkout gives its use back, a COD order split over
 * several Stores spends ONE use, refunds never give back more than was paid, and the Administrator manages the codes.
 * These tests commit; {@link SeedGuardV3} puts the test database back, so they only run on a {@code *_test} database.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=test-only-secret-test-only-secret-1234567890",
        "oneshop.checkout.online-enabled=true",
        "oneshop.payment.timeout-worker-enabled=false"})
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class PromotionDatabaseIntegrationTest {

    static final String ADMIN = "admin@oneshop.vn";
    static final String ALICE = "khachhang1@example.com";
    static final String BOB = "khachhang2@example.com";
    static final String STAFF_THUDUC = "staff.thuduc@oneshop.vn";
    static final String STAFF_GOVAP = "staff.govap@oneshop.vn";
    static final String SERUM = "SIMPLE-PURIFY-150";
    static final String SHAMPOO = "VASELINE-LIP-ORIG-48";
    static final String ADDRESS = FulfillmentFixtures.ADDRESS;

    @Autowired CheckoutService checkout;
    @Autowired CartService cartService;
    @Autowired CartRepository cartRepository;
    @Autowired CartItemRepository cartItemRepository;
    @Autowired ProductRepository productRepository;
    @Autowired StoreRepository storeRepository;
    @Autowired StoreProductRepository storeProductRepository;
    @Autowired CustomerOrderService customerOrders;
    @Autowired AdminPromotionService adminPromotions;
    @Autowired StaffFulfillmentService staff;
    @Autowired CustomerOrderReturnService customerReturns;
    @Autowired AdminOrderReturnService adminReturns;
    @MockitoBean CloudinaryService cloudinary;
    @Autowired JdbcTemplate jdbc;

    private SeedGuardV3 guard;
    private FulfillmentFixtures data;
    private boolean remembered;
    private int sequence;

    @BeforeEach
    void remember() {
        guard = new SeedGuardV3(jdbc);
        assumeTrue(guard.isTestDatabase(), "these tests commit: run with DB_NAME=oneshop_test");
        guard.remember();
        remembered = true;
        data = new FulfillmentFixtures(jdbc, cartService, checkout, cartRepository, cartItemRepository,
                productRepository, storeRepository, storeProductRepository);
        when(cloudinary.uploadImage(any(), anyString())).thenReturn(
                new com.oneshop.dto.response.ImageUploadResult("https://res.cloudinary.com/demo/image/upload/v1/returns/p.jpg", "returns/p"));
    }

    @AfterEach
    void restore() {
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

    private String uniqueCode(String prefix) {
        return prefix + (System.nanoTime() % 1_000_000_000L) + (sequence++);
    }

    /** Inserts a promotion directly; dates are given as SQL offsets in minutes (null = none). */
    private String promo(String type, int value, Integer cap, int min, int usageLimit, int perUser, Integer startMinutes,
                         Integer endMinutes, String status) {
        String code = uniqueCode("T");
        jdbc.update("insert into dbo.promotions (code, name, discount_type, discount_value, max_discount_amount, "
                        + "min_order_amount, usage_limit, used_count, per_user_limit, start_at, end_at, status) values "
                        + "(?, ?, ?, ?, ?, ?, ?, 0, ?, "
                        + (startMinutes == null ? "NULL" : "dateadd(minute, " + startMinutes + ", sysdatetime())") + ", "
                        + (endMinutes == null ? "NULL" : "dateadd(minute, " + endMinutes + ", sysdatetime())") + ", ?)",
                code, "Khuyến mãi thử " + code, type, value, cap, min, usageLimit, perUser, status);
        return code;
    }

    private String open(String type, int value, Integer cap, int min, int usageLimit) {
        return promo(type, value, cap, min, usageLimit, 5, null, null, "ACTIVE");
    }

    private long cartLine(String email, String sku, int quantity, boolean clear) {
        if (clear) {
            cartRepository.findByUserEmail(email).ifPresent(c -> cartItemRepository.deleteAll(cartItemRepository.findByCartId(c.getId())));
        }
        cartService.addItem(email, AddCartItemRequest.forProduct(data.productId(sku), quantity));
        return cartItemRepository.findByCartIdOrderByIdAsc(cartRepository.findByUserEmail(email).orElseThrow().getId()).stream()
                .filter(i -> i.getProduct().getSku().equals(sku)).findFirst().orElseThrow().getId();
    }

    /** The two-Store online cart (SERUM at Thu Duc + SHAMPOO at Go Vap): the ids of its two lines. */
    private List<Long> twoStoreCart(String email) {
        data.isolate(SERUM, "OS-THUDUC", 20);
        data.isolate(SHAMPOO, "OS-GOVAP", 20);
        long first = cartLine(email, SERUM, 1, true);
        long second = cartLine(email, SHAMPOO, 1, false);
        return List.of(first, second);
    }

    private CheckoutResultResponse place(String email, List<Long> lines, FulfillmentType type, PaymentMethod method,
                                         String code) {
        return checkout.placeOrder(email, new PlaceOrderRequest(lines, type, method, "Người Nhận", "0912345678",
                type == FulfillmentType.DELIVERY ? ADDRESS : null, null, false, null, null, code));
    }

    private long onlineOrder(String email, String code) {
        return place(email, twoStoreCart(email), FulfillmentType.DELIVERY, PaymentMethod.ONLINE, code).orders().get(0).orderId();
    }

    private BigDecimal money(String sql, Object... args) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal goods(long order) {
        return money("select coalesce(sum(subtotal), 0) from dbo.order_items where order_id = ?", order);
    }

    private int used(String code) {
        return jdbc.queryForObject("select used_count from dbo.promotions where code = ?", Integer.class, code);
    }

    private String usageStatus(String code) {
        return jdbc.queryForObject("select u.status from dbo.promotion_usages u join dbo.promotions p on "
                + "p.promotion_id = u.promotion_id where p.code = ?", String.class, code);
    }

    private int orders() {
        return jdbc.queryForObject("select count(*) from dbo.orders", Integer.class);
    }

    private void completeDelivery(String staffEmail, long part) {
        for (FulfillmentAction step : List.of(FulfillmentAction.PREPARE, FulfillmentAction.PACK, FulfillmentAction.SHIP,
                FulfillmentAction.COMPLETE_DELIVERY)) {
            data.asStaff(staffEmail, () -> staff.perform(part, step, step == FulfillmentAction.SHIP ? "TRK-" + part : null));
        }
    }

    // ================================================================== discount and totals

    @Test
    @DisplayName("PROMO-01 a percentage with a cap takes off the GOODS only; the total, the lines and the Payment follow it")
    void percentageWithCapOnTheGoodsOnly() {
        String code = open("PERCENT", 10, 50000, 100000, 10);
        List<Long> lines = twoStoreCart(ALICE);

        CheckoutPreviewResponse preview = checkout.preview(ALICE, lines, FulfillmentType.DELIVERY, PaymentMethod.ONLINE, null,
                ADDRESS, code);
        BigDecimal items = preview.plan().itemsTotal();
        BigDecimal expected = items.divide(BigDecimal.TEN).min(new BigDecimal("50000")).setScale(0, java.math.RoundingMode.DOWN);
        assertThat(preview.appliedPromotion().code()).isEqualTo(code);
        assertThat(preview.discountTotal()).isEqualByComparingTo(expected);
        assertThat(preview.grandTotal()).isEqualByComparingTo(items.subtract(expected).add(preview.shippingFeePerOrder()));
        assertThat(used(code)).as("a preview spends nothing").isZero();

        CheckoutResultResponse result = place(ALICE, lines, FulfillmentType.DELIVERY, PaymentMethod.ONLINE, code);
        long order = result.orders().get(0).orderId();

        assertThat(money("select discount_amount from dbo.orders where order_id = ?", order)).isEqualByComparingTo(expected);
        assertThat(jdbc.queryForObject("select promotion_code from dbo.orders where order_id = ?", String.class, order)).isEqualTo(code);
        BigDecimal fee = money("select shipping_fee from dbo.orders where order_id = ?", order);
        assertThat(fee).as("the shipping fee is not discounted").isEqualByComparingTo("30000");
        assertThat(money("select total_amount from dbo.orders where order_id = ?", order))
                .isEqualByComparingTo(goods(order).subtract(expected).add(fee));
        assertThat(money("select coalesce(sum(discount_amount), 0) from dbo.order_items where order_id = ?", order))
                .as("the lines share the discount to the exact VND").isEqualByComparingTo(expected);
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_items where order_id = ? and discount_amount > subtotal",
                Integer.class, order)).isZero();
        assertThat(money("select amount from dbo.payments where order_id = ?", order))
                .as("the payment obligation is the discounted total").isEqualByComparingTo(
                        money("select total_amount from dbo.orders where order_id = ?", order));
        assertThat(result.totalAmount()).isEqualByComparingTo(money("select total_amount from dbo.orders where order_id = ?", order));
        assertThat(used(code)).isEqualTo(1);
        assertThat(usageStatus(code)).isEqualTo("USED");
    }

    @Test
    @DisplayName("PROMO-02 a code that cannot be used refuses the order and changes nothing (min goods, dates, status, no uses left)")
    void unusableCodesRefuseTheOrderWholly() {
        List<Long> lines = twoStoreCart(ALICE);
        List<String> bad = List.of(
                promo("FIXED", 30000, null, 5_000_000, 10, 1, null, null, "ACTIVE"),
                promo("FIXED", 30000, null, 0, 10, 1, null, -10, "ACTIVE"),
                promo("FIXED", 30000, null, 0, 10, 1, 60, null, "ACTIVE"),
                promo("FIXED", 30000, null, 0, 10, 1, null, null, "INACTIVE"));
        String exhausted = open("FIXED", 30000, null, 0, 1);
        jdbc.update("update dbo.promotions set used_count = 1 where code = ?", exhausted);

        int ordersBefore = orders();
        for (String code : bad) {
            assertThatThrownBy(() -> place(ALICE, lines, FulfillmentType.DELIVERY, PaymentMethod.ONLINE, code))
                    .as(code).isInstanceOfSatisfying(CheckoutConflictException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo(CheckoutConflictException.CODE_PROMOTION_UNAVAILABLE));
        }
        assertThatThrownBy(() -> place(ALICE, lines, FulfillmentType.DELIVERY, PaymentMethod.ONLINE, exhausted))
                .isInstanceOf(CheckoutConflictException.class);
        assertThatThrownBy(() -> place(ALICE, lines, FulfillmentType.DELIVERY, PaymentMethod.ONLINE, "KHONG-TON-TAI"))
                .isInstanceOf(CheckoutConflictException.class);
        assertThat(orders()).as("no Order was created").isEqualTo(ordersBefore);
        assertThat(cartItemRepository.findByCartId(cartRepository.findByUserEmail(ALICE).orElseThrow().getId()))
                .as("the cart is untouched").hasSize(2);
        for (String code : bad) {
            assertThat(used(code)).isZero();
        }
        assertThat(used(exhausted)).isEqualTo(1);

        // the preview never throws: it says why, and the page lists the code as not usable
        CheckoutPreviewResponse preview = checkout.preview(ALICE, lines, FulfillmentType.DELIVERY, PaymentMethod.ONLINE, null,
                ADDRESS, bad.get(0));
        assertThat(preview.appliedPromotion()).isNull();
        assertThat(preview.promotionProblem()).contains("tiền hàng");
        assertThat(preview.discountTotal()).isEqualByComparingTo("0");
        assertThat(preview.promotions()).filteredOn(o -> o.code().equals(bad.get(0))).singleElement()
                .satisfies(o -> assertThat(o.eligible()).isFalse());
    }

    @Test
    @DisplayName("PROMO-03 each customer may use a code only per_user_limit times; another customer is not affected")
    void perCustomerLimit() {
        String code = promo("FIXED", 20000, null, 0, 10, 1, null, null, "ACTIVE");
        place(ALICE, twoStoreCart(ALICE), FulfillmentType.DELIVERY, PaymentMethod.ONLINE, code);

        List<Long> again = twoStoreCart(ALICE);
        assertThatThrownBy(() -> place(ALICE, again, FulfillmentType.DELIVERY, PaymentMethod.ONLINE, code))
                .isInstanceOfSatisfying(CheckoutConflictException.class, ex -> assertThat(ex.getMessage()).contains("số lần"));
        assertThat(checkout.preview(ALICE, again, FulfillmentType.DELIVERY, PaymentMethod.ONLINE, null, ADDRESS, code)
                .promotionProblem()).isNotNull();

        place(BOB, twoStoreCart(BOB), FulfillmentType.DELIVERY, PaymentMethod.ONLINE, code);
        assertThat(used(code)).isEqualTo(2);
    }

    @Test
    @DisplayName("PROMO-04 the LAST use goes to exactly one of two customers placing at the same moment")
    void lastUseIsSpentOnce() throws Exception {
        String code = open("FIXED", 25000, null, 0, 1);
        List<Long> alice = twoStoreCart(ALICE);
        List<Long> bob = twoStoreCart(BOB);
        int before = orders();

        List<Object> outcomes = race(
                () -> place(ALICE, alice, FulfillmentType.DELIVERY, PaymentMethod.ONLINE, code),
                () -> place(BOB, bob, FulfillmentType.DELIVERY, PaymentMethod.ONLINE, code));

        assertThat(outcomes.stream().filter(o -> o instanceof CheckoutResultResponse).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(o -> o instanceof CheckoutConflictException).count()).isEqualTo(1);
        assertThat(used(code)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from dbo.promotion_usages u join dbo.promotions p on p.promotion_id = "
                + "u.promotion_id where p.code = ?", Integer.class, code)).isEqualTo(1);
        assertThat(orders()).isEqualTo(before + 1);
    }

    // ================================================================== cancellation gives the use back

    @Test
    @DisplayName("PROMO-05 cancelling the Order gives the use back, and the customer may use the code again")
    void cancelGivesTheUseBack() {
        String code = promo("FIXED", 20000, null, 0, 10, 1, null, null, "ACTIVE");
        long order = onlineOrder(ALICE, code);
        assertThat(used(code)).isEqualTo(1);

        asCustomer(ALICE, () -> {
            customerOrders.cancelOrder(ALICE, order);
            return null;
        });

        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(used(code)).isZero();
        assertThat(usageStatus(code)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("select count(*) from dbo.promotion_usages u join dbo.promotions p on p.promotion_id = "
                + "u.promotion_id where p.code = ? and u.released_at is not null", Integer.class, code)).isEqualTo(1);
        // the one-use-per-customer limit no longer counts the cancelled order
        List<Long> again = twoStoreCart(ALICE);
        assertThat(checkout.preview(ALICE, again, FulfillmentType.DELIVERY, PaymentMethod.ONLINE, null, ADDRESS, code)
                .appliedPromotion()).isNotNull();
    }

    @Test
    @DisplayName("PROMO-06 a COD order split over two Stores spends ONE use, shares the discount exactly, and is released only when BOTH Orders are cancelled")
    void codSplitSpendsOneUse() {
        String code = promo("FIXED", 40001, null, 0, 10, 1, null, null, "ACTIVE");
        data.isolate(SERUM, "OS-THUDUC", 2);
        data.stockAt(SERUM, "OS-GOVAP", 2);
        long line = cartLine(ALICE, SERUM, 4, true);
        CheckoutPreviewResponse preview = checkout.preview(ALICE, List.of(line), FulfillmentType.DELIVERY, PaymentMethod.COD,
                null, ADDRESS, code);
        assertThat(preview.splitConsentRequired()).isTrue();
        assertThat(preview.orderCount()).isEqualTo(2);

        CheckoutResultResponse result = checkout.placeOrder(ALICE, new PlaceOrderRequest(List.of(line), FulfillmentType.DELIVERY,
                PaymentMethod.COD, "Người Nhận", "0912345678", ADDRESS, null, true, preview.plan().signature(),
                preview.grandTotal(), code));

        assertThat(result.orders()).hasSize(2);
        assertThat(used(code)).as("one use for the whole checkout").isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from dbo.promotion_usages", Integer.class)).isEqualTo(1);
        BigDecimal discounts = BigDecimal.ZERO;
        for (var summary : result.orders()) {
            long id = summary.orderId();
            BigDecimal share = money("select discount_amount from dbo.orders where order_id = ?", id);
            discounts = discounts.add(share);
            assertThat(money("select total_amount from dbo.orders where order_id = ?", id))
                    .isEqualByComparingTo(goods(id).subtract(share).add(new BigDecimal("30000")));
            assertThat(money("select coalesce(sum(discount_amount), 0) from dbo.order_items where order_id = ?", id)).isEqualByComparingTo(share);
        }
        assertThat(discounts).as("the shares add up to the whole discount").isEqualByComparingTo("40001");
        assertThat(result.totalAmount()).isEqualByComparingTo(preview.grandTotal());

        asCustomer(ALICE, () -> {
            customerOrders.cancelOrder(ALICE, result.orders().get(0).orderId());
            return null;
        });
        assertThat(used(code)).as("one Order is still alive").isEqualTo(1);
        assertThat(usageStatus(code)).isEqualTo("USED");
        asCustomer(ALICE, () -> {
            customerOrders.cancelOrder(ALICE, result.orders().get(1).orderId());
            return null;
        });
        assertThat(used(code)).isZero();
        assertThat(usageStatus(code)).isEqualTo("RELEASED");
    }

    // ================================================================== refunds stay inside what was paid

    @Test
    @DisplayName("PROMO-07 a returned discounted Order is refunded the DISCOUNTED goods (never the full price); the use stays spent")
    void returnRefundsTheDiscountedGoods() {
        String code = open("PERCENT", 20, null, 0, 10);
        long order = onlineOrder(ALICE, code);
        jdbc.update("update dbo.payments set status = 'SUCCESS', paid_at = sysdatetime() where order_id = ?", order);
        data.markPaid(order);
        jdbc.update("update dbo.payments set refund_status = 'NONE' where order_id = ?", order);
        completeDelivery(STAFF_THUDUC, data.fulfillmentOf(order, "OS-THUDUC"));
        completeDelivery(STAFF_GOVAP, data.fulfillmentOf(order, "OS-GOVAP"));
        assertThat(data.orderStatus(order)).isEqualTo("COMPLETED");
        BigDecimal paid = money("select amount from dbo.payments where order_id = ? and status = 'SUCCESS'", order);
        BigDecimal net = goods(order).subtract(money("select discount_amount from dbo.orders where order_id = ?", order));
        assertThat(net).isLessThan(goods(order));

        OrderReturnResponse made = asCustomer(ALICE, () -> customerReturns.request(ALICE, order, "Sản phẩm bị lỗi khi mở hộp",
                List.of(new MockMultipartFile("images", "p.jpg", "image/jpeg", new byte[]{1, 2, 3}))));
        assertThat(made.refundAmount()).as("net of the promotion").isEqualByComparingTo(net);
        asAdmin(() -> adminReturns.approve(made.returnId(), ADMIN));

        assertThat(money("select amount from dbo.payment_refunds where order_id = ? and component = 'RETURN'", order))
                .isEqualByComparingTo(net);
        assertThat(paid.subtract(net)).as("what the shop keeps: the shipping fee").isEqualByComparingTo("30000");
        assertThat(used(code)).as("a delivered Order does not give its use back").isEqualTo(1);
        assertThat(usageStatus(code)).isEqualTo("USED");
    }

    @Test
    @DisplayName("PROMO-08 an undeliverable part is refunded its DISCOUNTED goods: collected - refunded = the delivered part + the shipping fee")
    void undeliverablePartRefundsTheDiscountedGoods() {
        String code = open("FIXED", 30000, null, 0, 10);
        long order = onlineOrder(ALICE, code);
        data.markPaid(order);
        jdbc.update("update dbo.payments set refund_status = 'NONE' where order_id = ?", order);
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        long goVap = data.fulfillmentOf(order, "OS-GOVAP");
        completeDelivery(STAFF_GOVAP, goVap);
        for (FulfillmentAction step : List.of(FulfillmentAction.PREPARE, FulfillmentAction.PACK, FulfillmentAction.SHIP)) {
            data.asStaff(STAFF_THUDUC, () -> staff.perform(thuDuc, step, step == FulfillmentAction.SHIP ? "TRK-" + thuDuc : null));
        }
        data.asStaff(STAFF_THUDUC, () -> staff.perform(thuDuc, FulfillmentAction.FAIL_DELIVERY, "Khách vắng nhà"));
        BigDecimal collected = money("select amount from dbo.payments where order_id = ? and status = 'SUCCESS'", order);

        asAdmin(() -> adminExceptions.markUndeliverable(order, thuDuc,
                com.oneshop.service.fulfillment.AdminFulfillmentExceptionService.GoodsStatus.RETURNED, "Khách hủy nhận phần này"));

        BigDecimal netThuDuc = money("select sum(subtotal - discount_amount) from dbo.order_items where fulfillment_id = ?", thuDuc);
        BigDecimal netGoVap = money("select sum(subtotal - discount_amount) from dbo.order_items where fulfillment_id = ?", goVap);
        BigDecimal refunded = money("select coalesce(sum(amount), 0) from dbo.payment_refunds where order_id = ?", order);
        assertThat(refunded).isEqualByComparingTo(netThuDuc);
        assertThat(collected.subtract(refunded)).isEqualByComparingTo(netGoVap.add(new BigDecimal("30000")));
        assertThat(refunded).isLessThan(money("select sum(subtotal) from dbo.order_items where fulfillment_id = ?", thuDuc));
    }

    @Autowired com.oneshop.service.fulfillment.AdminFulfillmentExceptionService adminExceptions;

    // ================================================================== administrator

    private PromotionRequest form(String code, String type, String value) {
        PromotionRequest request = new PromotionRequest();
        request.setCode(code);
        request.setName("Khuyến mãi " + code);
        request.setDiscountType(PromotionType.valueOf(type));
        request.setDiscountValue(new BigDecimal(value));
        request.setMinOrderAmount(new BigDecimal("100000"));
        request.setUsageLimit(5);
        request.setPerUserLimit(1);
        request.setStatus(PromotionStatus.ACTIVE);
        return request;
    }

    @Test
    @DisplayName("PROMO-09 the Administrator creates, edits and deletes codes under the rules (unique code, valid values, limit not below the uses spent, a used code is not deleted)")
    void adminManagesTheCodes() {
        String code = uniqueCode("a");
        PromotionResponse created = asAdmin(() -> adminPromotions.create(form(code.toLowerCase(), "PERCENT", "15")));
        assertThat(created.code()).as("stored upper-case").isEqualTo(code.toUpperCase());
        assertThat(created.state()).isEqualTo("ACTIVE");
        assertThat(created.usedCount()).isZero();

        assertThatThrownBy(() -> asAdmin(() -> adminPromotions.create(form(code, "PERCENT", "10"))))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("đã tồn tại");
        assertThatThrownBy(() -> asAdmin(() -> adminPromotions.create(form("ab", "FIXED", "1000"))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> asAdmin(() -> adminPromotions.create(form(uniqueCode("b"), "PERCENT", "150"))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> asAdmin(() -> adminPromotions.create(form(uniqueCode("c"), "FIXED", "1000.5"))))
                .isInstanceOf(BadRequestException.class);
        PromotionRequest capOnFixed = form(uniqueCode("d"), "FIXED", "1000");
        capOnFixed.setMaxDiscountAmount(new BigDecimal("500"));
        assertThatThrownBy(() -> asAdmin(() -> adminPromotions.create(capOnFixed))).isInstanceOf(BadRequestException.class);
        PromotionRequest backwards = form(uniqueCode("e"), "FIXED", "1000");
        backwards.setStartAt(java.time.LocalDateTime.now().plusDays(2));
        backwards.setEndAt(java.time.LocalDateTime.now().plusDays(1));
        assertThatThrownBy(() -> asAdmin(() -> adminPromotions.create(backwards))).isInstanceOf(BadRequestException.class);

        PromotionRequest edit = form(code, "FIXED", "25000");
        edit.setUsageLimit(7);
        PromotionResponse edited = asAdmin(() -> adminPromotions.update(created.id(), edit));
        assertThat(edited.discountType()).isEqualTo(PromotionType.FIXED);
        assertThat(edited.usageLimit()).isEqualTo(7);
        assertThat(edited.code()).as("the code never changes").isEqualTo(created.code());

        // a used code: the limit cannot go below the uses spent, and it cannot be deleted
        jdbc.update("update dbo.promotions set used_count = 3 where promotion_id = ?", created.id());
        jdbc.update("insert into dbo.checkout_sessions (user_id, total_amount, status) select top 1 user_id, 0, 'CREATED' from dbo.users where email = ?", ALICE);
        long checkout = jdbc.queryForObject("select max(checkout_id) from dbo.checkout_sessions", Long.class);
        jdbc.update("insert into dbo.promotion_usages (promotion_id, user_id, checkout_id, discount_amount) select ?, user_id, ?, 0 from dbo.users where email = ?",
                created.id(), checkout, ALICE);
        PromotionRequest tooLow = form(code, "FIXED", "25000");
        tooLow.setUsageLimit(2);
        assertThatThrownBy(() -> asAdmin(() -> adminPromotions.update(created.id(), tooLow)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("3");
        assertThatThrownBy(() -> asAdmin(() -> {
            adminPromotions.delete(created.id());
            return null;
        })).isInstanceOf(BadRequestException.class).hasMessageContaining("không thể xóa");
        PromotionRequest off = form(code, "FIXED", "25000");
        off.setStatus(PromotionStatus.INACTIVE);
        assertThat(asAdmin(() -> adminPromotions.update(created.id(), off)).state()).isEqualTo("INACTIVE");
        assertThat(asAdmin(() -> adminPromotions.recentUsages(created.id()))).hasSize(1);

        // an unused code is deleted
        PromotionResponse spare = asAdmin(() -> adminPromotions.create(form(uniqueCode("f"), "PERCENT", "5")));
        asAdmin(() -> {
            adminPromotions.delete(spare.id());
            return null;
        });
        assertThat(jdbc.queryForObject("select count(*) from dbo.promotions where promotion_id = ?", Integer.class, spare.id())).isZero();
    }

    @Test
    @DisplayName("PROMO-10 only an Administrator manages promotions")
    void onlyAdminManages() {
        assertThatThrownBy(() -> data.asStaff(STAFF_THUDUC, () -> adminPromotions.list(null, null, 0)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> asCustomer(ALICE, () -> adminPromotions.create(form(uniqueCode("g"), "FIXED", "1000"))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("PROMO-11 the signature the customer confirmed covers the code and the discount: another code is a different plan")
    void signatureCoversThePromotion() {
        String code = open("FIXED", 30000, null, 0, 10);
        List<Long> lines = twoStoreCart(ALICE);
        CheckoutPreviewResponse with = checkout.preview(ALICE, lines, FulfillmentType.DELIVERY, PaymentMethod.ONLINE, null, ADDRESS, code);
        CheckoutPreviewResponse without = checkout.preview(ALICE, lines, FulfillmentType.DELIVERY, PaymentMethod.ONLINE, null, ADDRESS, null);
        assertThat(with.plan().signature()).isNotEqualTo(without.plan().signature());
        assertThat(with.grandTotal()).isLessThan(without.grandTotal());

        // confirming the plan of "no code" while also sending a code is refused: the plan is not what was shown
        assertThatThrownBy(() -> checkout.placeOrder(ALICE, new PlaceOrderRequest(lines, FulfillmentType.DELIVERY,
                PaymentMethod.ONLINE, "Người Nhận", "0912345678", ADDRESS, null, false, without.plan().signature(),
                without.grandTotal(), code))).isInstanceOf(CheckoutConflictException.class);
        assertThat(used(code)).isZero();
    }

    // ------------------------------------------------------------------ concurrency

    private List<Object> race(Callable<?>... tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> task : tasks) {
                futures.add(pool.submit(() -> {
                    go.await();
                    try {
                        return task.call();
                    } catch (Exception ex) {
                        return ex;
                    }
                }));
            }
            go.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
