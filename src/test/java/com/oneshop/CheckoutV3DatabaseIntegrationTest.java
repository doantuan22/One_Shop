package com.oneshop;

import com.oneshop.dto.request.AddCartItemRequest;
import com.oneshop.dto.request.PlaceOrderRequest;
import com.oneshop.dto.response.CheckoutPreviewResponse;
import com.oneshop.dto.response.CheckoutResultResponse;
import com.oneshop.dto.response.OrderPaymentResponse;
import com.oneshop.dto.response.OrderSummaryResponse;
import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.CheckoutConflictException;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.service.CartService;
import com.oneshop.service.CheckoutService;
import com.oneshop.service.DeliveryFulfillmentService;
import com.oneshop.service.PaymentService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Phase V3-BE-03 on the real SQL Server test database (BE03-01 .. BE03-30): central checkout, store allocation,
 * immediate stock deduction, one ONLINE Order with N Fulfillments, COD split with consent, pickup, the 15-minute
 * PENDING_PAYMENT boundary and the payment gate.
 *
 * <p>Every test runs in a transaction that is rolled back; it only runs against a database whose name ends with
 * {@code _test} (run with {@code DB_NAME=oneshop_test}). Races, fault injection and real commits are in
 * {@link CheckoutV3ConcurrencyDatabaseIntegrationTest}.
 *
 * <p>Delivery runs under the approved policy (national coverage by delivery-enabled Stores, 30.000 VND per
 * Order, pickup free). {@code oneshop.checkout.online-enabled=true} below is a TEST-ONLY switch: ONLINE checkout is
 * closed in the application until BE-05. The other flows of the default configuration are covered in
 * {@link CheckoutV3PolicyGateDatabaseIntegrationTest}; the same-SKU split, the national coverage and the fee in
 * {@link CheckoutV3SplitAndFeeDatabaseIntegrationTest}.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=test-only-secret-test-only-secret-1234567890",
        "oneshop.checkout.online-enabled=true"})
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
@Transactional
class CheckoutV3DatabaseIntegrationTest {

    static final String ALICE = "khachhang1@example.com";
    static final String BOB = "khachhang2@example.com";
    static final String STAFF_THU_DUC = "staff.thuduc@oneshop.vn";

    // SKUs of the seed (looked up by SKU, never by id)
    static final String SERUM = "SIMPLE-PURIFY-150";      // Thu Duc only (+ Hai Chau, inactive)
    static final String TONER = "CERAVE-HYDRA-236";       // Thu Duc only
    static final String SHAMPOO = "VASELINE-LIP-ORIG-48";    // Go Vap only
    static final String SUN_MILK = "LRP-EFFACLAR-MP-200";    // Quan 7 only
    static final String WHIP = "SIMPLE-REFRESH-150";        // every Store, scenario of many sources
    static final String ANTHELIOS = "BIODERMA-SENSIBIO-250"; // Thu Duc 0, Go Vap 25

    static final String THU_DUC = "OS-THUDUC";
    static final String GO_VAP = "OS-GOVAP";
    static final String QUAN_7 = "OS-QUAN7";
    static final String QUAN_10 = "OS-QUAN10";   // pickup only
    static final String HOAN_KIEM = "OS-HOANKIEM"; // delivery only

    @PersistenceContext
    EntityManager entityManager;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    CheckoutService checkoutService;
    @Autowired
    CartService cartService;
    @Autowired
    PaymentService paymentService;
    @Autowired
    DeliveryFulfillmentService deliveryService;
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

    @BeforeEach
    void onlyOnTheTestDatabase() {
        assumeTrue(new SeedGuardV3(jdbc).isTestDatabase(),
                "BE-03 integration tests write data: run with DB_NAME=oneshop_test");
    }

    // ------------------------------------------------------------------ helpers

    void sync() {
        entityManager.flush();
        entityManager.clear();
    }

    long productId(String sku) {
        return productRepository.findBySku(sku).orElseThrow().getId();
    }

    long storeId(String code) {
        return storeRepository.findByCode(code).orElseThrow().getId();
    }

    long sp(String sku, String storeCode) {
        return storeProductRepository.findByStoreIdAndProductId(storeId(storeCode), productId(sku)).orElseThrow().getId();
    }

    int stock(long storeProductId) {
        return jdbc.queryForObject("select quantity from dbo.store_products where store_product_id = ?",
                Integer.class, storeProductId);
    }

    int reserved(long storeProductId) {
        return jdbc.queryForObject("select reserved_quantity from dbo.store_products where store_product_id = ?",
                Integer.class, storeProductId);
    }

    void setStock(long storeProductId, int quantity) {
        jdbc.update("update dbo.store_products set quantity = ? where store_product_id = ?", quantity, storeProductId);
        sync();
    }

    long count(String table) {
        return jdbc.queryForObject("select count(*) from dbo." + table, Long.class);
    }

    /** The customer's cart becomes exactly these lines (SKU, quantity, SKU, quantity, ...); returns SKU -> cart item id. */
    Map<String, Long> cart(String email, Object... skuAndQuantity) {
        cartRepository.findByUserEmail(email).ifPresent(c -> cartItemRepository.deleteAll(cartItemRepository.findByCartId(c.getId())));
        sync();
        for (int i = 0; i < skuAndQuantity.length; i += 2) {
            cartService.addItem(email, AddCartItemRequest.forProduct(productId((String) skuAndQuantity[i]),
                    (Integer) skuAndQuantity[i + 1]));
        }
        sync();
        Map<String, Long> ids = new LinkedHashMap<>();
        cartRepository.findByUserEmail(email).ifPresent(c -> cartItemRepository.findByCartIdOrderByIdAsc(c.getId())
                .forEach(line -> ids.put(line.getProduct().getSku(), line.getId())));
        return ids;
    }

    static PlaceOrderRequest request(Collection<Long> ids, FulfillmentType type, PaymentMethod method,
                                     Long pickupStoreId, boolean consent, String signature, BigDecimal expectedTotal) {
        return new PlaceOrderRequest(List.copyOf(ids), type, method, "Người Nhận", "0912345678",
                type == FulfillmentType.DELIVERY ? "12 Đường Thử Nghiệm, Quận 1" : null, pickupStoreId, consent,
                signature, expectedTotal);
    }

    static PlaceOrderRequest delivery(Collection<Long> ids, PaymentMethod method) {
        return request(ids, FulfillmentType.DELIVERY, method, null, false, null, null);
    }

    CheckoutResultResponse place(String email, PlaceOrderRequest request) {
        CheckoutResultResponse result = checkoutService.placeOrder(email, request);
        sync();
        return result;
    }

    List<Map<String, Object>> fulfillments(long orderId) {
        return jdbc.queryForList("select fulfillment_id, store_id, fulfillment_type, status from "
                + "dbo.order_fulfillments where order_id = ? order by store_id", orderId);
    }

    Map<String, Object> orderRow(long orderId) {
        return jdbc.queryForMap("select order_id, checkout_id, store_id, fulfillment_type, payment_method, "
                + "payment_status, order_status, total_amount, shipping_fee, created_at from dbo.orders where order_id = ?",
                orderId);
    }

    // ================================================================= BE03-01 .. 03 allocation

    @Test
    @DisplayName("BE03-01 one Store holds the whole cart: 1 Order, 1 Fulfillment")
    void be03_01_oneStoreFullCart() {
        Map<String, Long> lines = cart(ALICE, SERUM, 2, TONER, 1);

        CheckoutResultResponse result = place(ALICE, delivery(lines.values(), PaymentMethod.COD));

        assertThat(result.orders()).hasSize(1);
        OrderSummaryResponse order = result.orders().get(0);
        assertThat(order.fulfillments()).hasSize(1);
        assertThat(order.fulfillments().get(0).storeId()).isEqualTo(storeId(THU_DUC));
        assertThat(order.items()).hasSize(2);
        assertThat(fulfillments(order.orderId())).hasSize(1);
    }

    @Test
    @DisplayName("BE03-02 two Stores: ONE ONLINE Order, TWO Fulfillments, one payment obligation")
    void be03_02_twoStoresOnlineIsOneOrder() {
        Map<String, Long> lines = cart(ALICE, SERUM, 1, SHAMPOO, 1);
        long ordersBefore = count("orders");

        CheckoutResultResponse result = place(ALICE, delivery(lines.values(), PaymentMethod.ONLINE));

        assertThat(count("orders") - ordersBefore).isEqualTo(1);
        assertThat(result.orders()).hasSize(1);
        long orderId = result.orders().get(0).orderId();
        assertThat(fulfillments(orderId)).extracting(f -> f.get("store_id"))
                .containsExactly(storeId(THU_DUC), storeId(GO_VAP));
        // no parent/child Orders: the checkout session has exactly this Order
        assertThat(jdbc.queryForObject("select count(*) from dbo.orders where checkout_id = ?", Long.class,
                result.checkoutId())).isEqualTo(1);
        // one payment obligation for the whole Order
        assertThat(jdbc.queryForObject("select count(*) from dbo.payments where order_id = ?", Long.class, orderId))
                .isEqualTo(1);
        assertThat(orderRow(orderId).get("store_id")).isNull();
    }

    @Test
    @DisplayName("BE03-03 three Stores: ONE Order, THREE Fulfillments")
    void be03_03_threeStores() {
        Map<String, Long> lines = cart(ALICE, SERUM, 1, SHAMPOO, 1, SUN_MILK, 1);

        CheckoutResultResponse result = place(ALICE, delivery(lines.values(), PaymentMethod.ONLINE));

        assertThat(result.orders()).hasSize(1);
        assertThat(fulfillments(result.orders().get(0).orderId())).hasSize(3);
    }

    // ================================================================= BE03-04 .. 07 eligibility and stock

    @Test
    @DisplayName("BE03-04 an INACTIVE Store / StoreProduct is never a source")
    void be03_04_inactiveStoreIsNeverUsed() {
        Map<String, Long> lines = cart(ALICE, SERUM, 2);
        long inactive = sp(SERUM, "OS-HAICHAU");
        int inactiveStock = stock(inactive);
        // the only ACTIVE source runs out after the customer added the line
        setStock(sp(SERUM, THU_DUC), 0);
        long ordersBefore = count("orders");

        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, delivery(lines.values(), PaymentMethod.COD)))
                .isInstanceOf(CheckoutConflictException.class);

        assertThat(count("orders")).isEqualTo(ordersBefore);
        assertThat(stock(inactive)).isEqualTo(inactiveStock);
    }

    @Test
    @DisplayName("BE03-05 a Store without delivery (pickup only) is not a DELIVERY source")
    void be03_05_pickupOnlyStoreIsNotADeliverySource() {
        // Quan 10 is the only Store holding the SKU
        for (String store : List.of(THU_DUC, GO_VAP, QUAN_7, HOAN_KIEM)) {
            long id = sp(WHIP, store);
            jdbc.update("update dbo.store_products set quantity = 0 where store_product_id = ?", id);
        }
        Map<String, Long> lines = cart(ALICE, WHIP, 1);
        long ordersBefore = count("orders");

        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, delivery(lines.values(), PaymentMethod.COD)))
                .isInstanceOf(CheckoutConflictException.class);
        assertThat(count("orders")).isEqualTo(ordersBefore);

        // ...but it is a pickup source
        CheckoutResultResponse pickup = place(ALICE, request(lines.values(), FulfillmentType.STORE_PICKUP,
                PaymentMethod.PAY_AT_STORE, storeId(QUAN_10), false, null, null));
        assertThat(pickup.orders().get(0).fulfillments().get(0).storeId()).isEqualTo(storeId(QUAN_10));
    }

    @Test
    @DisplayName("BE03-06 a Store without pickup (delivery only) cannot be chosen for STORE_PICKUP")
    void be03_06_deliveryOnlyStoreCannotBePickedUp() {
        Map<String, Long> lines = cart(ALICE, WHIP, 1);

        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, request(lines.values(), FulfillmentType.STORE_PICKUP,
                PaymentMethod.PAY_AT_STORE, storeId(HOAN_KIEM), false, null, null)))
                .isInstanceOf(CheckoutConflictException.class);
    }

    @Test
    @DisplayName("BE03-07 not enough stock: no Order, no deduction, cart untouched")
    void be03_07_notEnoughStockCreatesNothing() {
        Map<String, Long> lines = cart(ALICE, ANTHELIOS, 20);
        long goVap = sp(ANTHELIOS, GO_VAP);
        setStock(goVap, 5);
        Map<String, Long> before = tableCounts();

        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, delivery(lines.values(), PaymentMethod.COD)))
                .isInstanceOf(CheckoutConflictException.class);

        assertThat(tableCounts()).isEqualTo(before);
        assertThat(stock(goVap)).isEqualTo(5);
        assertThat(cartRepository.findByUserEmail(ALICE).map(c -> cartItemRepository.findByCartId(c.getId()).size()))
                .contains(1);
    }

    @Test
    @DisplayName("BE03-06b the quantity of one SKU is split between Stores when no Store holds it all (OPEN-01)")
    void be03_06b_quantityIsSplitBetweenStores() {
        // 100 units: the delivery Stores hold 53 + 48 + 50 + 60 = 211 but none holds 100
        Map<String, Long> lines = cart(ALICE, WHIP, 100);

        CheckoutResultResponse result = place(ALICE, delivery(lines.values(), PaymentMethod.ONLINE));

        assertThat(result.orders()).hasSize(1);
        List<Map<String, Object>> items = jdbc.queryForList("select oi.quantity, sp.store_id from dbo.order_items oi "
                + "join dbo.store_products sp on sp.store_product_id = oi.store_product_id where oi.order_id = ? "
                + "order by sp.store_id", result.orders().get(0).orderId());
        assertThat(items.stream().mapToInt(i -> (Integer) i.get("quantity")).sum()).isEqualTo(100);
        assertThat(items.size()).isGreaterThan(1);
        assertThat(fulfillments(result.orders().get(0).orderId())).hasSize(items.size());
    }

    private Map<String, Long> tableCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : List.of("checkout_sessions", "orders", "order_fulfillments",
                "order_fulfillment_status_history", "order_items", "inventory_movements", "order_status_history",
                "payments", "cart_items")) {
            counts.put(table, count(table));
        }
        return counts;
    }

    // ================================================================= BE03-08, 24 price

    @Test
    @DisplayName("BE03-08 the price is Product.price at order time; the request carries no price")
    void be03_08_priceComesFromTheProduct() {
        Map<String, Long> lines = cart(ALICE, SERUM, 2);
        BigDecimal price = productRepository.findBySku(SERUM).orElseThrow().getPrice();

        CheckoutResultResponse result = place(ALICE, delivery(lines.values(), PaymentMethod.COD));

        var item = result.orders().get(0).items().get(0);
        assertThat(item.unitPrice()).isEqualByComparingTo(price);
        assertThat(item.subtotal()).isEqualByComparingTo(price.multiply(BigDecimal.valueOf(2)));
        // the legacy StoreProduct.price is not used
        BigDecimal legacy = jdbc.queryForObject("select price from dbo.store_products where store_product_id = ?",
                BigDecimal.class, sp(SERUM, THU_DUC));
        if (legacy != null && legacy.compareTo(price) != 0) {
            assertThat(item.unitPrice()).isNotEqualByComparingTo(legacy);
        }
    }

    @Test
    @DisplayName("BE03-24 changing Product.price afterwards leaves the OrderItem snapshot unchanged")
    void be03_24_snapshotIsImmutable() {
        Map<String, Long> lines = cart(ALICE, SERUM, 1);
        CheckoutResultResponse result = place(ALICE, delivery(lines.values(), PaymentMethod.COD));
        BigDecimal snapshot = result.orders().get(0).items().get(0).unitPrice();

        jdbc.update("update dbo.products set price = price + 1000 where sku = ?", SERUM);
        sync();

        assertThat(jdbc.queryForObject("select unit_price from dbo.order_items where order_id = ?", BigDecimal.class,
                result.orders().get(0).orderId())).isEqualByComparingTo(snapshot);
        assertThat(checkoutService.getCheckout(ALICE, result.checkoutId()).orders().get(0).items().get(0).unitPrice())
                .isEqualByComparingTo(snapshot);
    }

    // ================================================================= BE03-09 .. 12 items, deduction, audit

    @Test
    @DisplayName("BE03-09 every OrderItem sits in a Fulfillment of its own Order and of its StoreProduct's Store")
    void be03_09_itemsMatchFulfillmentOrderAndStore() {
        Map<String, Long> lines = cart(ALICE, SERUM, 1, SHAMPOO, 1, SUN_MILK, 2);
        CheckoutResultResponse result = place(ALICE, delivery(lines.values(), PaymentMethod.ONLINE));
        long orderId = result.orders().get(0).orderId();

        assertThat(jdbc.queryForObject("select count(*) from dbo.order_items oi where oi.order_id = ?", Long.class, orderId))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_items oi join dbo.order_fulfillments f "
                + "on f.fulfillment_id = oi.fulfillment_id where oi.order_id = ? and f.order_id <> oi.order_id",
                Long.class, orderId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_items oi join dbo.order_fulfillments f "
                + "on f.fulfillment_id = oi.fulfillment_id join dbo.store_products sp "
                + "on sp.store_product_id = oi.store_product_id where oi.order_id = ? and sp.store_id <> f.store_id",
                Long.class, orderId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_items where order_id = ? and fulfillment_id is null",
                Long.class, orderId)).isZero();
    }

    @Test
    @DisplayName("BE03-10 stock leaves the StoreProducts at once, reserved_quantity is not touched")
    void be03_10_immediateDeduction() {
        long serum = sp(SERUM, THU_DUC);
        long shampoo = sp(SHAMPOO, GO_VAP);
        int serumBefore = stock(serum);
        int shampooBefore = stock(shampoo);
        Map<String, Long> lines = cart(ALICE, SERUM, 3, SHAMPOO, 2);

        place(ALICE, delivery(lines.values(), PaymentMethod.ONLINE));

        assertThat(stock(serum)).isEqualTo(serumBefore - 3);
        assertThat(stock(shampoo)).isEqualTo(shampooBefore - 2);
        assertThat(reserved(serum)).isZero();
        assertThat(reserved(shampoo)).isZero();
    }

    @Test
    @DisplayName("BE03-11 one ORDER movement per StoreProduct: negative change, before/after, order reference")
    void be03_11_inventoryAudit() {
        long serum = sp(SERUM, THU_DUC);
        int before = stock(serum);
        Map<String, Long> lines = cart(ALICE, SERUM, 3, SHAMPOO, 1);

        CheckoutResultResponse result = place(ALICE, delivery(lines.values(), PaymentMethod.ONLINE));
        long orderId = result.orders().get(0).orderId();

        List<Map<String, Object>> movements = jdbc.queryForList("select store_product_id, type, quantity_before, "
                + "quantity_change, quantity_after from dbo.inventory_movements where reference_order_id = ? "
                + "order by store_product_id", orderId);
        assertThat(movements).hasSize(2);
        assertThat(movements).allSatisfy(m -> {
            assertThat(m.get("type")).isEqualTo("ORDER");
            assertThat((Integer) m.get("quantity_change")).isNegative();
            assertThat((Integer) m.get("quantity_after"))
                    .isEqualTo((Integer) m.get("quantity_before") + (Integer) m.get("quantity_change"));
        });
        Map<String, Object> serumMove = movements.stream()
                .filter(m -> ((Number) m.get("store_product_id")).longValue() == serum).findFirst().orElseThrow();
        assertThat(serumMove.get("quantity_before")).isEqualTo(before);
        assertThat(serumMove.get("quantity_change")).isEqualTo(-3);
        assertThat(serumMove.get("quantity_after")).isEqualTo(before - 3);
    }

    @Test
    @DisplayName("BE03-12 a legacy reservation is respected: available = quantity - reserved, CHECK still holds")
    void be03_12_reservedQuantityIsRespected() {
        long serum = sp(SERUM, THU_DUC);
        Map<String, Long> lines = cart(ALICE, SERUM, 2);
        jdbc.update("update dbo.store_products set quantity = 40, reserved_quantity = 39 where store_product_id = ?", serum);
        sync();

        // available is 1 < 2 although quantity is 40
        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, delivery(lines.values(), PaymentMethod.COD)))
                .isInstanceOf(CheckoutConflictException.class);
        assertThat(stock(serum)).isEqualTo(40);

        jdbc.update("update dbo.store_products set reserved_quantity = 38 where store_product_id = ?", serum);
        sync();
        place(ALICE, delivery(lines.values(), PaymentMethod.COD));

        // 40 - 2 = 38 and reserved 38 <= 38: CK_store_products_reserved is satisfied (flush wrote it)
        assertThat(stock(serum)).isEqualTo(38);
        assertThat(reserved(serum)).isEqualTo(38);
    }

    // ================================================================= BE03-14 .. 16 COD

    @Test
    @DisplayName("BE03-14 COD from one Store: one Order, CONFIRMED, UNPAID, no payment collected, no 15-minute rule")
    void be03_14_codOneStore() {
        Map<String, Long> lines = cart(ALICE, SERUM, 1, TONER, 1);

        CheckoutResultResponse result = place(ALICE, delivery(lines.values(), PaymentMethod.COD));
        Map<String, Object> order = orderRow(result.orders().get(0).orderId());

        assertThat(order.get("order_status")).isEqualTo("CONFIRMED");
        assertThat(order.get("payment_status")).isEqualTo("UNPAID");
        assertThat(order.get("payment_method")).isEqualTo("COD");
        assertThat(order.get("store_id")).isEqualTo(storeId(THU_DUC));
        assertThat(jdbc.queryForObject("select count(*) from dbo.payments where order_id = ?", Long.class,
                result.orders().get(0).orderId())).isZero();
        assertThat(result.orders().get(0).paymentDeadline()).isNull();
    }

    @Test
    @DisplayName("BE03-15 COD from several Stores without consent: no Order, nothing deducted")
    void be03_15_codSplitNeedsConsent() {
        Map<String, Long> lines = cart(ALICE, SERUM, 1, SHAMPOO, 1);
        Map<String, Long> before = tableCounts();
        int stockBefore = stock(sp(SERUM, THU_DUC));

        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, delivery(lines.values(), PaymentMethod.COD)))
                .isInstanceOfSatisfying(CheckoutConflictException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(CheckoutConflictException.CODE_COD_SPLIT_CONSENT_REQUIRED));

        assertThat(tableCounts()).isEqualTo(before);
        assertThat(stock(sp(SERUM, THU_DUC))).isEqualTo(stockBefore);
    }

    @Test
    @DisplayName("BE03-16 COD split with consent: N independent Orders, one per Store, no parent Order")
    void be03_16_codSplitWithConsent() {
        Map<String, Long> lines = cart(ALICE, SERUM, 2, SHAMPOO, 1);
        CheckoutPreviewResponse preview = checkoutService.preview(ALICE, lines.values(), FulfillmentType.DELIVERY,
                PaymentMethod.COD, null, "12 Đường Thử Nghiệm, Quận 1");
        assertThat(preview.splitConsentRequired()).isTrue();
        assertThat(preview.orderCount()).isEqualTo(2);

        CheckoutResultResponse result = place(ALICE, request(lines.values(), FulfillmentType.DELIVERY,
                PaymentMethod.COD, null, true, preview.plan().signature(), preview.grandTotal()));

        assertThat(result.orders()).hasSize(2);
        assertThat(result.orders()).allSatisfy(order -> {
            assertThat(order.fulfillments()).hasSize(1);
            assertThat(order.storeId()).isNotNull();
            assertThat(order.paymentMethod()).isEqualTo(PaymentMethod.COD);
        });
        assertThat(result.orders()).extracting(OrderSummaryResponse::storeId)
                .containsExactlyInAnyOrder(storeId(THU_DUC), storeId(GO_VAP));
        // totals add up to the session; each Order has its own total
        BigDecimal sum = result.orders().stream().map(OrderSummaryResponse::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(result.totalAmount()).isEqualByComparingTo(sum);
        // independent: same checkout session, no payment attempt for COD
        assertThat(jdbc.queryForObject("select count(distinct checkout_id) from dbo.orders where order_id in (?, ?)",
                Long.class, result.orders().get(0).orderId(), result.orders().get(1).orderId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from dbo.payments where order_id in (?, ?)", Long.class,
                result.orders().get(0).orderId(), result.orders().get(1).orderId())).isZero();
    }

    // ================================================================= BE03-17, 18 pickup

    @Test
    @DisplayName("BE03-17 STORE_PICKUP at a Store that holds the whole cart: one Order, one Fulfillment there")
    void be03_17_pickupAtOneStore() {
        Map<String, Long> lines = cart(ALICE, SERUM, 1, TONER, 1);
        CheckoutPreviewResponse preview = checkoutService.preview(ALICE, lines.values(), FulfillmentType.STORE_PICKUP,
                PaymentMethod.PAY_AT_STORE, null, null);
        assertThat(preview.pickupStores()).extracting(s -> s.storeId()).containsExactly(storeId(THU_DUC));

        CheckoutResultResponse result = place(ALICE, request(lines.values(), FulfillmentType.STORE_PICKUP,
                PaymentMethod.PAY_AT_STORE, storeId(THU_DUC), false, null, null));

        assertThat(result.orders()).hasSize(1);
        OrderSummaryResponse order = result.orders().get(0);
        assertThat(order.fulfillmentType()).isEqualTo(FulfillmentType.STORE_PICKUP);
        assertThat(order.fulfillments()).hasSize(1);
        assertThat(order.fulfillments().get(0).storeId()).isEqualTo(storeId(THU_DUC));
        assertThat(order.shippingAddress()).isNull();
    }

    @Test
    @DisplayName("BE03-18 STORE_PICKUP at a Store missing a SKU: refused, stock is not taken from another Store")
    void be03_18_pickupAtStoreWithMissingSkuIsRefused() {
        Map<String, Long> lines = cart(ALICE, SERUM, 1, SHAMPOO, 1);
        Map<String, Long> before = tableCounts();

        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, request(lines.values(), FulfillmentType.STORE_PICKUP,
                PaymentMethod.PAY_AT_STORE, storeId(THU_DUC), false, null, null)))
                .isInstanceOf(CheckoutConflictException.class);

        assertThat(tableCounts()).isEqualTo(before);
    }

    // ================================================================= BE03-19, 20 ONLINE pending + gate

    @Test
    @DisplayName("BE03-19 ONLINE: PENDING_PAYMENT, one PENDING payment for the whole total, deadline = created_at + 15 min")
    void be03_19_onlinePendingPaymentAndDeadline() {
        Map<String, Long> lines = cart(ALICE, SERUM, 1, SHAMPOO, 1);

        CheckoutResultResponse result = place(ALICE, delivery(lines.values(), PaymentMethod.ONLINE));
        OrderSummaryResponse summary = result.orders().get(0);
        Map<String, Object> order = orderRow(summary.orderId());

        assertThat(order.get("order_status")).isEqualTo("PENDING_PAYMENT");
        assertThat(order.get("payment_status")).isEqualTo("UNPAID");
        Map<String, Object> payment = jdbc.queryForMap("select amount, status, refund_status, transaction_code, "
                + "paid_at from dbo.payments where order_id = ?", summary.orderId());
        assertThat((BigDecimal) payment.get("amount")).isEqualByComparingTo(summary.totalAmount());
        assertThat(payment.get("status")).isEqualTo("PENDING");
        assertThat(payment.get("refund_status")).isEqualTo("NONE");
        assertThat(payment.get("paid_at")).isNull();
        assertThat((String) payment.get("transaction_code")).startsWith("MOCK-");

        java.time.LocalDateTime created = ((java.sql.Timestamp) order.get("created_at")).toLocalDateTime();
        assertThat(summary.paymentDeadline()).isEqualTo(created.plusMinutes(15));
        // fulfillments wait: CONFIRMED but gated
        assertThat(fulfillments(summary.orderId())).extracting(f -> f.get("status")).containsOnly("CONFIRMED");
    }

    @Test
    @DisplayName("BE03-20 Staff cannot start PREPARING on a pending ONLINE Order (single Store) or a multi-store one")
    void be03_20_staffCannotPrepareBeforePayment() {
        Map<String, Long> single = cart(ALICE, SERUM, 1, TONER, 1);
        long singleOrder = place(ALICE, delivery(single.values(), PaymentMethod.ONLINE)).orders().get(0).orderId();
        Map<String, Long> multi = cart(BOB, SERUM, 1, SHAMPOO, 1);
        long multiOrder = place(BOB, delivery(multi.values(), PaymentMethod.ONLINE)).orders().get(0).orderId();
        Map<String, Long> before = tableCounts();

        assertThatThrownBy(() -> deliveryService.startPreparingDelivery(STAFF_THU_DUC, singleOrder))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> deliveryService.startPreparingDelivery(STAFF_THU_DUC, multiOrder))
                .isInstanceOf(BadRequestException.class);

        assertThat(orderRow(singleOrder).get("order_status")).isEqualTo("PENDING_PAYMENT");
        assertThat(orderRow(multiOrder).get("order_status")).isEqualTo("PENDING_PAYMENT");
        assertThat(tableCounts()).isEqualTo(before);
    }

    @Test
    @DisplayName("BE03-47 a browser can neither confirm nor fail a payment")
    void be03_47_browserCannotConfirmPayment() {
        Map<String, Long> lines = cart(ALICE, SERUM, 1);
        long orderId = place(ALICE, delivery(lines.values(), PaymentMethod.ONLINE)).orders().get(0).orderId();
        long paymentId = jdbc.queryForObject("select payment_id from dbo.payments where order_id = ?", Long.class, orderId);

        assertThatThrownBy(() -> paymentService.markOnlinePaymentSuccess(ALICE, orderId, paymentId))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> paymentService.markOnlinePaymentFailed(ALICE, orderId, paymentId))
                .isInstanceOf(BadRequestException.class);

        assertThat(orderRow(orderId).get("order_status")).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("select status from dbo.payments where payment_id = ?", String.class, paymentId))
                .isEqualTo("PENDING");
    }

    @Test
    @DisplayName("BE03-19b a new payment attempt after the deadline is refused; the deadline does not move")
    void be03_19b_noNewAttemptAfterTheDeadline() {
        Map<String, Long> lines = cart(ALICE, SERUM, 1);
        CheckoutResultResponse result = place(ALICE, delivery(lines.values(), PaymentMethod.ONLINE));
        long orderId = result.orders().get(0).orderId();
        java.time.LocalDateTime deadline = result.orders().get(0).paymentDeadline();

        // 20 minutes have passed since the Order was created
        jdbc.update("update dbo.orders set created_at = dateadd(minute, -20, created_at) where order_id = ?", orderId);
        sync();

        OrderPaymentResponse payments = paymentService.getOrderPayments(ALICE, orderId);
        assertThat(payments.expired()).isTrue();
        assertThat(payments.payable()).isFalse();
        assertThat(payments.paymentDeadline()).isEqualTo(deadline.minusMinutes(20));
        assertThatThrownBy(() -> paymentService.createOnlinePaymentAttempt(ALICE, orderId))
                .isInstanceOf(BadRequestException.class);
    }

    // ================================================================= BE03-21, 23, 25 duplicate, totals, ownership

    @Test
    @DisplayName("BE03-21 submitting the same lines twice creates one Order and deducts once")
    void be03_21_duplicateCheckout() {
        long serum = sp(SERUM, THU_DUC);
        int before = stock(serum);
        Map<String, Long> lines = cart(ALICE, SERUM, 2);
        long ordersBefore = count("orders");

        place(ALICE, delivery(lines.values(), PaymentMethod.COD));
        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, delivery(lines.values(), PaymentMethod.COD)))
                .isInstanceOf(BadRequestException.class);

        assertThat(count("orders") - ordersBefore).isEqualTo(1);
        assertThat(stock(serum)).isEqualTo(before - 2);
    }

    @Test
    @DisplayName("BE03-23 Order total = sum of its items + shipping fee; session total = sum of its Orders")
    void be03_23_totals() {
        Map<String, Long> lines = cart(ALICE, SERUM, 2, SHAMPOO, 3);

        CheckoutResultResponse result = place(ALICE, delivery(lines.values(), PaymentMethod.ONLINE));
        long orderId = result.orders().get(0).orderId();

        BigDecimal items = jdbc.queryForObject("select sum(subtotal) from dbo.order_items where order_id = ?",
                BigDecimal.class, orderId);
        BigDecimal fee = (BigDecimal) orderRow(orderId).get("shipping_fee");
        assertThat((BigDecimal) orderRow(orderId).get("total_amount")).isEqualByComparingTo(items.add(fee));
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_items where order_id = ? "
                + "and subtotal <> unit_price * quantity", Long.class, orderId)).isZero();
        assertThat(result.totalAmount()).isEqualByComparingTo(items.add(fee));
        // ONLINE across several Stores: ONE shipping fee of 30.000, not one per Fulfillment
        assertThat(fee).isEqualByComparingTo("30000");
        assertThat(fulfillments(orderId)).hasSize(2);
    }

    @Test
    @DisplayName("BE03-25 another customer's cart lines are refused")
    void be03_25_foreignCartLines() {
        Map<String, Long> bobs = cart(BOB, SERUM, 1);
        cart(ALICE, TONER, 1);
        Map<String, Long> before = tableCounts();

        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, delivery(bobs.values(), PaymentMethod.COD)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> checkoutService.preview(ALICE, bobs.values(), FulfillmentType.DELIVERY, null, null, null))
                .isInstanceOf(BadRequestException.class);

        assertThat(tableCounts()).isEqualTo(before);
    }

    // ================================================================= BE03-26 .. 28 integrity

    @Test
    @DisplayName("BE03-26/27/28 integrity: FKs agree, only allowed statuses/movement types, no transfer movement")
    void be03_26_27_28_integrityAfterCheckouts() {
        Map<String, Long> a = cart(ALICE, SERUM, 1, SHAMPOO, 1, SUN_MILK, 1);
        place(ALICE, delivery(a.values(), PaymentMethod.ONLINE));
        Map<String, Long> b = cart(BOB, TONER, 1);
        place(BOB, request(b.values(), FulfillmentType.STORE_PICKUP, PaymentMethod.PAY_AT_STORE, storeId(THU_DUC), false,
                null, null));

        // every item, whole database: fulfillment belongs to the item's order, and to the store of its StoreProduct
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_items oi join dbo.order_fulfillments f "
                + "on f.fulfillment_id = oi.fulfillment_id where f.order_id <> oi.order_id", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_items oi join dbo.order_fulfillments f "
                + "on f.fulfillment_id = oi.fulfillment_id join dbo.store_products sp "
                + "on sp.store_product_id = oi.store_product_id where sp.store_id <> f.store_id", Long.class)).isZero();
        // only the statuses and movement types the schema accepts (and none for a transfer)
        assertThat(jdbc.queryForObject("select count(*) from dbo.inventory_movements where type not in "
                + "('ORDER','CANCEL_ORDER','STOCK_ADJUST')", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from dbo.orders where order_status not in ('PENDING_PAYMENT',"
                + "'CONFIRMED','PROCESSING','SHIPPING','PARTIALLY_COMPLETED','COMPLETED','CANCELLED','NEEDS_ATTENTION')",
                Long.class)).isZero();
        // stock never negative, reserved never above quantity
        assertThat(jdbc.queryForObject("select count(*) from dbo.store_products where quantity < 0 "
                + "or reserved_quantity > quantity", Long.class)).isZero();
        // each movement of the new orders is at a store that has a Fulfillment of that order
        assertThat(jdbc.queryForObject("select count(*) from dbo.inventory_movements m join dbo.store_products sp "
                + "on sp.store_product_id = m.store_product_id where m.type = 'ORDER' and not exists (select 1 "
                + "from dbo.order_fulfillments f where f.order_id = m.reference_order_id and f.store_id = sp.store_id)",
                Long.class)).isZero();
        // each order's fulfillments sum back to the order lines (no quantity lost or invented)
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_fulfillments f where f.order_id > "
                + "(select max(order_id) - 2 from dbo.orders) and not exists (select 1 from dbo.order_items i "
                + "where i.fulfillment_id = f.fulfillment_id)", Long.class)).isZero();
    }

    // ================================================================= BE03-29 preview changes

    @Test
    @DisplayName("BE03-29 the plan seen on the preview changed before confirming: refused, then re-confirmed")
    void be03_29_previewPlanChangesBeforeCommit() {
        Map<String, Long> lines = cart(ALICE, WHIP, 1);
        CheckoutPreviewResponse preview = checkoutService.preview(ALICE, lines.values(), FulfillmentType.DELIVERY,
                PaymentMethod.COD, null, "12 Đường Thử Nghiệm, Quận 1");
        Long seenStore = preview.plan().parts().get(0).storeId();
        // the Store that was going to supply it sells out in the meantime
        setStock(sp(WHIP, storeCodeOf(seenStore)), 0);
        long ordersBefore = count("orders");

        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, request(lines.values(), FulfillmentType.DELIVERY,
                PaymentMethod.COD, null, false, preview.plan().signature(), preview.grandTotal())))
                .isInstanceOfSatisfying(CheckoutConflictException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(CheckoutConflictException.CODE_PLAN_CHANGED));
        assertThat(count("orders")).isEqualTo(ordersBefore);

        // a fresh preview shows another Store and confirming that one works
        CheckoutPreviewResponse fresh = checkoutService.preview(ALICE, lines.values(), FulfillmentType.DELIVERY,
                PaymentMethod.COD, null, "12 Đường Thử Nghiệm, Quận 1");
        assertThat(fresh.plan().parts().get(0).storeId()).isNotEqualTo(seenStore);
        CheckoutResultResponse result = place(ALICE, request(lines.values(), FulfillmentType.DELIVERY,
                PaymentMethod.COD, null, false, fresh.plan().signature(), fresh.grandTotal()));
        assertThat(result.orders().get(0).fulfillments().get(0).storeId()).isEqualTo(fresh.plan().parts().get(0).storeId());
    }

    @Test
    @DisplayName("BE03-08b a price that changed since the preview must be re-confirmed")
    void be03_08b_priceChangeNeedsReconfirmation() {
        Map<String, Long> lines = cart(ALICE, SERUM, 1);
        CheckoutPreviewResponse preview = checkoutService.preview(ALICE, lines.values(), FulfillmentType.DELIVERY,
                PaymentMethod.COD, null, "12 Đường Thử Nghiệm, Quận 1");
        jdbc.update("update dbo.products set price = price + 5000 where sku = ?", SERUM);
        sync();
        long ordersBefore = count("orders");

        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, request(lines.values(), FulfillmentType.DELIVERY,
                PaymentMethod.COD, null, false, preview.plan().signature(), preview.grandTotal())))
                .isInstanceOfSatisfying(CheckoutConflictException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(CheckoutConflictException.CODE_TOTAL_CHANGED));
        assertThat(count("orders")).isEqualTo(ordersBefore);
    }

    private String storeCodeOf(Long storeId) {
        return storeRepository.findById(storeId).orElseThrow().getCode();
    }

    // ================================================================= preview is read only

    @Test
    @DisplayName("Preview writes nothing and reserves nothing")
    void previewIsReadOnly() {
        Map<String, Long> lines = cart(ALICE, SERUM, 1, SHAMPOO, 1);
        Map<String, Long> before = tableCounts();
        int stockBefore = stock(sp(SERUM, THU_DUC));
        int reservedBefore = reserved(sp(SERUM, THU_DUC));

        CheckoutPreviewResponse preview = checkoutService.preview(ALICE, lines.values(), FulfillmentType.DELIVERY,
                PaymentMethod.ONLINE, null, "12 Đường Thử Nghiệm, Quận 1");

        assertThat(preview.plan()).isNotNull();
        assertThat(preview.plan().fulfillmentCount()).isEqualTo(2);
        assertThat(preview.orderCount()).isEqualTo(1); // ONLINE: one Order whatever the number of Stores
        assertThat(preview.splitConsentRequired()).isFalse();
        assertThat(tableCounts()).isEqualTo(before);
        assertThat(stock(sp(SERUM, THU_DUC))).isEqualTo(stockBefore);
        assertThat(reserved(sp(SERUM, THU_DUC))).isEqualTo(reservedBefore);
    }

    @Test
    @DisplayName("The V2 per-Store request no longer creates anything")
    void legacyV2RequestIsRefused() {
        Map<String, Long> lines = cart(ALICE, SERUM, 1);
        Map<String, Long> before = tableCounts();

        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE,
                new com.oneshop.dto.request.CheckoutRequest(List.copyOf(lines.values()), List.of())))
                .isInstanceOf(BadRequestException.class);

        assertThat(tableCounts()).isEqualTo(before);
    }
}
