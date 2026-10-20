package com.oneshop;

import com.oneshop.config.CheckoutProperties;
import com.oneshop.dto.request.AddCartItemRequest;
import com.oneshop.dto.request.PlaceOrderRequest;
import com.oneshop.dto.response.CheckoutPreviewResponse;
import com.oneshop.dto.response.CheckoutResultResponse;
import com.oneshop.dto.response.OrderSummaryResponse;
import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.CheckoutConflictException;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.CartService;
import com.oneshop.service.CheckoutService;
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
 * Phase V3-BE-03 completion on SQL Server (BE03-F01 .. F30): same-SKU quantity split between Stores (OPEN-01),
 * national delivery coverage by delivery-enabled Stores (OPEN-02) and the fixed shipping fee of 30.000 VND per Order
 * (OPEN-03), through the default application configuration. Rolled back; only against a {@code *_test} database.
 *
 * <p>ONLINE checkout is closed in the application until BE-05, so {@code oneshop.checkout.online-enabled=true} is a
 * TEST-ONLY switch used to exercise the one-Order / many-Fulfillment shape.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=test-only-secret-test-only-secret-1234567890",
        "oneshop.checkout.online-enabled=true"})
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
@Transactional
class CheckoutV3SplitAndFeeDatabaseIntegrationTest {

    private static final String ALICE = "khachhang1@example.com";
    private static final String BOB = "khachhang2@example.com";
    private static final String HCM_ADDRESS = "12 Lê Lợi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh";

    private static final String WHIP = "SIMPLE-REFRESH-150";      // sold at every Store
    private static final String SERUM = "SIMPLE-PURIFY-150";    // Thu Duc only
    private static final String SHAMPOO = "VASELINE-LIP-ORIG-48";  // Go Vap only
    private static final String SUN_MILK = "LRP-EFFACLAR-MP-200";  // Quan 7 only
    private static final String TONER = "CERAVE-HYDRA-236";     // Thu Duc only

    private static final List<String> ALL_STORES =
            List.of("OS-THUDUC", "OS-GOVAP", "OS-QUAN7", "OS-QUAN10", "OS-HOANKIEM");

    @PersistenceContext
    EntityManager entityManager;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    CheckoutService checkoutService;
    @Autowired
    CartService cartService;
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
    CheckoutProperties checkoutProperties;

    @BeforeEach
    void onlyOnTheTestDatabase() {
        assumeTrue(new SeedGuardV3(jdbc).isTestDatabase(), "run with DB_NAME=oneshop_test");
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

    BigDecimal price(String sku) {
        return productRepository.findBySku(sku).orElseThrow().getPrice();
    }

    /** SENKA-WHIP stock per Store: the listed Stores get the quantity, every other Store 0. */
    void whipStock(Map<String, Integer> byStore) {
        for (String code : ALL_STORES) {
            jdbc.update("update dbo.store_products set quantity = ?, reserved_quantity = 0 where store_product_id = ?",
                    byStore.getOrDefault(code, 0), sp(WHIP, code));
        }
        sync();
    }

    long count(String table) {
        return jdbc.queryForObject("select count(*) from dbo." + table, Long.class);
    }

    Map<String, Long> tableCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : List.of("checkout_sessions", "orders", "order_fulfillments", "order_items",
                "inventory_movements", "order_status_history", "payments", "cart_items")) {
            counts.put(table, count(table));
        }
        return counts;
    }

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

    static PlaceOrderRequest request(Collection<Long> ids, FulfillmentType type, PaymentMethod method, Long pickupStoreId,
                                     String address, boolean consent, String signature, BigDecimal expectedTotal) {
        return new PlaceOrderRequest(List.copyOf(ids), type, method, "Người Nhận", "0912345678",
                type == FulfillmentType.DELIVERY ? address : null, pickupStoreId, consent, signature, expectedTotal);
    }

    CheckoutPreviewResponse preview(String email, Collection<Long> ids, FulfillmentType type, PaymentMethod method,
                                    Long pickupStoreId) {
        return checkoutService.preview(email, ids, type, method, pickupStoreId,
                type == FulfillmentType.DELIVERY ? HCM_ADDRESS : null);
    }

    /** Preview then confirm exactly what was previewed (signature, total, consent if a split is needed). */
    CheckoutResultResponse confirm(String email, Collection<Long> ids, FulfillmentType type, PaymentMethod method,
                                   Long pickupStoreId) {
        CheckoutPreviewResponse preview = preview(email, ids, type, method, pickupStoreId);
        assertThat(preview.plan()).as(preview.planProblem()).isNotNull();
        CheckoutResultResponse result = checkoutService.placeOrder(email, request(ids, type, method, pickupStoreId,
                HCM_ADDRESS, preview.splitConsentRequired(), preview.plan().signature(), preview.grandTotal()));
        sync();
        return result;
    }

    List<Map<String, Object>> items(long orderId) {
        return jdbc.queryForList("select oi.quantity, oi.unit_price, oi.subtotal, oi.store_product_id, sp.store_id, "
                + "f.store_id as fulfillment_store, f.fulfillment_id from dbo.order_items oi "
                + "join dbo.store_products sp on sp.store_product_id = oi.store_product_id "
                + "join dbo.order_fulfillments f on f.fulfillment_id = oi.fulfillment_id "
                + "where oi.order_id = ? order by sp.store_id, oi.order_item_id", orderId);
    }

    BigDecimal shippingFee(long orderId) {
        return jdbc.queryForObject("select shipping_fee from dbo.orders where order_id = ?", BigDecimal.class, orderId);
    }

    BigDecimal total(long orderId) {
        return jdbc.queryForObject("select total_amount from dbo.orders where order_id = ?", BigDecimal.class, orderId);
    }

    // ================================================================= same-SKU quantity split (OPEN-01)

    @Test
    @DisplayName("BE03-F02 5 units of one SKU, Stores hold 3 and 2: ONE Order, TWO Fulfillments, two OrderItems of one SKU")
    void f02_sameSkuSplitOverTwoStores() {
        whipStock(Map.of("OS-THUDUC", 3, "OS-GOVAP", 2));
        long thuDuc = sp(WHIP, "OS-THUDUC");
        long goVap = sp(WHIP, "OS-GOVAP");
        Map<String, Long> lines = cart(ALICE, WHIP, 5);
        long ordersBefore = count("orders");

        CheckoutResultResponse result = confirm(ALICE, lines.values(), FulfillmentType.DELIVERY, PaymentMethod.ONLINE, null);

        assertThat(count("orders") - ordersBefore).isEqualTo(1);
        OrderSummaryResponse order = result.orders().get(0);
        assertThat(order.fulfillments()).hasSize(2);
        List<Map<String, Object>> items = items(order.orderId());
        assertThat(items).hasSize(2);
        assertThat(items.stream().mapToInt(i -> (Integer) i.get("quantity")).sum()).isEqualTo(5);
        assertThat(items.get(0).get("quantity")).isEqualTo(3);
        assertThat(items.get(1).get("quantity")).isEqualTo(2);
        // each item sits in the Fulfillment of the Store that supplies it, at the single price
        assertThat(items).allSatisfy(i -> {
            assertThat(i.get("store_id")).isEqualTo(i.get("fulfillment_store"));
            assertThat((BigDecimal) i.get("unit_price")).isEqualByComparingTo(price(WHIP));
        });
        assertThat(items.get(0).get("store_product_id")).isNotEqualTo(items.get(1).get("store_product_id"));
        // stock left both StoreProducts, one movement each
        assertThat(stock(thuDuc)).isZero();
        assertThat(stock(goVap)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from dbo.inventory_movements where reference_order_id = ? "
                + "and type = 'ORDER' and quantity_change < 0", Long.class, order.orderId())).isEqualTo(2);
        // one fee, total = 5 x price + 30.000, one payment for it
        assertThat(shippingFee(order.orderId())).isEqualByComparingTo("30000");
        assertThat(total(order.orderId())).isEqualByComparingTo(price(WHIP).multiply(BigDecimal.valueOf(5)).add(new BigDecimal("30000")));
        assertThat(jdbc.queryForObject("select amount from dbo.payments where order_id = ?", BigDecimal.class,
                order.orderId())).isEqualByComparingTo(total(order.orderId()));
    }

    @Test
    @DisplayName("BE03-F03 one SKU over three Stores: quantities add up, every Store movement is its own")
    void f03_sameSkuSplitOverThreeStores() {
        whipStock(Map.of("OS-THUDUC", 2, "OS-GOVAP", 2, "OS-QUAN7", 2));
        Map<String, Long> lines = cart(ALICE, WHIP, 5);

        CheckoutResultResponse result = confirm(ALICE, lines.values(), FulfillmentType.DELIVERY, PaymentMethod.ONLINE, null);

        long orderId = result.orders().get(0).orderId();
        assertThat(result.orders().get(0).fulfillments()).hasSize(3);
        assertThat(items(orderId).stream().map(i -> (Integer) i.get("quantity")).toList()).containsExactly(2, 2, 1);
        assertThat(stock(sp(WHIP, "OS-THUDUC"))).isZero();
        assertThat(stock(sp(WHIP, "OS-GOVAP"))).isZero();
        assertThat(stock(sp(WHIP, "OS-QUAN7"))).isEqualTo(1);
        assertThat(shippingFee(orderId)).isEqualByComparingTo("30000"); // three Fulfillments, still one fee
    }

    @Test
    @DisplayName("BE03-F04 a cart with several SKUs where one SKU needs the split")
    void f04_mixedCartWithOneSplitSku() {
        whipStock(Map.of("OS-THUDUC", 3, "OS-GOVAP", 2));
        Map<String, Long> lines = cart(ALICE, SERUM, 1, WHIP, 5);

        CheckoutResultResponse result = confirm(ALICE, lines.values(), FulfillmentType.DELIVERY, PaymentMethod.ONLINE, null);

        long orderId = result.orders().get(0).orderId();
        assertThat(result.orders().get(0).fulfillments()).hasSize(2);
        assertThat(items(orderId)).hasSize(3); // serum + whip x3 at Thu Duc, whip x2 at Go Vap
        assertThat(jdbc.queryForObject("select sum(oi.quantity) from dbo.order_items oi join dbo.store_products sp "
                + "on sp.store_product_id = oi.store_product_id where oi.order_id = ? and sp.product_id = ?",
                Integer.class, orderId, productId(WHIP))).isEqualTo(5);
    }

    @Test
    @DisplayName("BE03-F05 the eligible Stores together are short: no Order, nothing deducted, cart intact")
    void f05_totalShortageCreatesNothing() {
        // Quan 10 (pickup only) holds 40 so the cart accepts 5, but the delivery Stores hold 2 + 2
        whipStock(Map.of("OS-THUDUC", 2, "OS-GOVAP", 2, "OS-QUAN10", 40));
        Map<String, Long> lines = cart(ALICE, WHIP, 5);
        Map<String, Long> before = tableCounts();

        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, request(lines.values(), FulfillmentType.DELIVERY,
                PaymentMethod.COD, null, HCM_ADDRESS, false, null, null)))
                .isInstanceOf(CheckoutConflictException.class);

        assertThat(tableCounts()).isEqualTo(before);
        assertThat(stock(sp(WHIP, "OS-THUDUC"))).isEqualTo(2);
        // ...but the same cart can be collected at Quan 10, which holds it all
        CheckoutResultResponse pickup = checkoutService.placeOrder(ALICE, request(lines.values(),
                FulfillmentType.STORE_PICKUP, PaymentMethod.PAY_AT_STORE, storeId("OS-QUAN10"), null, false, null, null));
        assertThat(pickup.orders().get(0).fulfillments()).hasSize(1);
    }

    @Test
    @DisplayName("BE03-F06/F07 an INACTIVE Store and an INACTIVE StoreProduct never supply, even with stock")
    void f06_f07_inactiveSourcesAreNotUsed() {
        whipStock(Map.of("OS-THUDUC", 10, "OS-GOVAP", 3));
        Map<String, Long> lines = cart(ALICE, WHIP, 5);
        long thuDuc = sp(WHIP, "OS-THUDUC");
        // Thu Duc becomes unavailable after the customer added the line: Go Vap alone has 3 < 5
        jdbc.update("update dbo.stores set status = 'INACTIVE' where code = 'OS-THUDUC'");
        sync();
        Map<String, Long> before = tableCounts();
        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, request(lines.values(), FulfillmentType.DELIVERY,
                PaymentMethod.COD, null, HCM_ADDRESS, false, null, null))).isInstanceOf(CheckoutConflictException.class);
        assertThat(tableCounts()).isEqualTo(before);
        assertThat(stock(thuDuc)).isEqualTo(10);

        jdbc.update("update dbo.stores set status = 'ACTIVE' where code = 'OS-THUDUC'");
        jdbc.update("update dbo.store_products set status = 'INACTIVE' where store_product_id = ?", thuDuc);
        sync();
        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, request(lines.values(), FulfillmentType.DELIVERY,
                PaymentMethod.COD, null, HCM_ADDRESS, false, null, null))).isInstanceOf(CheckoutConflictException.class);
        assertThat(stock(thuDuc)).isEqualTo(10);
    }

    @Test
    @DisplayName("BE03-F08 reserved_quantity > 0: only quantity - reserved is allocated and the CHECK still holds")
    void f08_reservedQuantityLimitsWhatIsAllocated() {
        whipStock(Map.of("OS-THUDUC", 5, "OS-GOVAP", 3));
        long thuDuc = sp(WHIP, "OS-THUDUC");
        jdbc.update("update dbo.store_products set reserved_quantity = 3 where store_product_id = ?", thuDuc); // 2 available
        sync();
        Map<String, Long> lines = cart(ALICE, WHIP, 5);

        CheckoutResultResponse result = confirm(ALICE, lines.values(), FulfillmentType.DELIVERY, PaymentMethod.ONLINE, null);

        // Go Vap gives 3, Thu Duc only the 2 that are not reserved
        assertThat(result.orders().get(0).fulfillments()).hasSize(2);
        assertThat(stock(thuDuc)).isEqualTo(3);
        assertThat(reserved(thuDuc)).isEqualTo(3);
        assertThat(stock(sp(WHIP, "OS-GOVAP"))).isZero();
    }

    @Test
    @DisplayName("BE03-F09 the same data gives the same plan and the same fingerprint")
    void f09_planIsDeterministic() {
        whipStock(Map.of("OS-THUDUC", 3, "OS-GOVAP", 2));
        Map<String, Long> lines = cart(ALICE, WHIP, 5);

        CheckoutPreviewResponse first = preview(ALICE, lines.values(), FulfillmentType.DELIVERY, PaymentMethod.ONLINE, null);
        CheckoutPreviewResponse second = preview(ALICE, lines.values(), FulfillmentType.DELIVERY, PaymentMethod.ONLINE, null);

        assertThat(second.plan().signature()).isEqualTo(first.plan().signature());
        assertThat(first.plan().parts()).extracting(p -> p.lines().get(0).quantity()).containsExactly(3, 2);
        assertThat(first.orderCount()).isEqualTo(1);
        assertThat(first.grandTotal()).isEqualByComparingTo(price(WHIP).multiply(BigDecimal.valueOf(5)).add(new BigDecimal("30000")));
    }

    // ================================================================= national delivery (OPEN-02)

    @Test
    @DisplayName("BE03-F13/F14/F18 a Store in Ha Noi serves a Ho Chi Minh address; no Store is chosen by the customer")
    void f13_northernStoreServesASouthernAddress() {
        whipStock(Map.of("OS-HOANKIEM", 5));
        Map<String, Long> lines = cart(ALICE, WHIP, 2);

        CheckoutResultResponse result = confirm(ALICE, lines.values(), FulfillmentType.DELIVERY, PaymentMethod.COD, null);

        assertThat(result.orders().get(0).fulfillments()).hasSize(1);
        assertThat(result.orders().get(0).fulfillments().get(0).storeId()).isEqualTo(storeId("OS-HOANKIEM"));
        assertThat(result.orders().get(0).shippingAddress()).isEqualTo(HCM_ADDRESS);
    }

    @Test
    @DisplayName("BE03-F15 a delivery-disabled Store (pickup only) never supplies DELIVERY, whatever its stock")
    void f15_deliveryDisabledStoreIsNotUsed() {
        whipStock(Map.of("OS-QUAN10", 40, "OS-GOVAP", 1));
        Map<String, Long> lines = cart(ALICE, WHIP, 3);

        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, request(lines.values(), FulfillmentType.DELIVERY,
                PaymentMethod.COD, null, HCM_ADDRESS, false, null, null))).isInstanceOf(CheckoutConflictException.class);
    }

    @Test
    @DisplayName("BE03-F17 an address that cannot be a delivery address is refused before anything is created")
    void f17_invalidAddressIsRefused() {
        Map<String, Long> lines = cart(ALICE, SERUM, 1);
        Map<String, Long> before = tableCounts();

        for (String address : new String[]{null, "", "   ", "abc", "1234567890", "x y z", "<script>alert(1)</script> abc def",
                "a".repeat(501)}) {
            assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, request(lines.values(), FulfillmentType.DELIVERY,
                    PaymentMethod.COD, null, address, false, null, null)))
                    .as("address: " + address).isInstanceOf(BadRequestException.class);
        }
        assertThat(tableCounts()).isEqualTo(before);
    }

    @Test
    @DisplayName("BE03-F19/F20 a pickup Store without the whole cart is refused; stock is not borrowed")
    void f19_f20_pickupDoesNotBorrow() {
        Map<String, Long> lines = cart(ALICE, SERUM, 1, SHAMPOO, 1);
        Map<String, Long> before = tableCounts();

        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, request(lines.values(), FulfillmentType.STORE_PICKUP,
                PaymentMethod.PAY_AT_STORE, storeId("OS-THUDUC"), null, false, null, null)))
                .isInstanceOf(CheckoutConflictException.class);
        assertThat(tableCounts()).isEqualTo(before);
        // pickup is not split either: a Store holding only part of one SKU is refused
        whipStock(Map.of("OS-THUDUC", 2, "OS-GOVAP", 2));
        Map<String, Long> whip = cart(ALICE, WHIP, 4);
        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, request(whip.values(), FulfillmentType.STORE_PICKUP,
                PaymentMethod.PAY_AT_STORE, storeId("OS-THUDUC"), null, false, null, null)))
                .isInstanceOf(CheckoutConflictException.class);
    }

    // ================================================================= shipping fee (OPEN-03)

    @Test
    @DisplayName("BE03-F21/F22/F23 ONLINE delivery: 1, 2 and 3 Stores all cost one 30.000 fee")
    void f21_f22_f23_onlineFeeIsOnePerOrder() {
        Map<String, Long> one = cart(ALICE, SERUM, 1, TONER, 1);
        CheckoutResultResponse r1 = confirm(ALICE, one.values(), FulfillmentType.DELIVERY, PaymentMethod.ONLINE, null);
        Map<String, Long> two = cart(ALICE, SERUM, 1, SHAMPOO, 1);
        CheckoutResultResponse r2 = confirm(ALICE, two.values(), FulfillmentType.DELIVERY, PaymentMethod.ONLINE, null);
        Map<String, Long> three = cart(ALICE, SERUM, 1, SHAMPOO, 1, SUN_MILK, 1);
        CheckoutResultResponse r3 = confirm(ALICE, three.values(), FulfillmentType.DELIVERY, PaymentMethod.ONLINE, null);

        assertThat(r1.orders().get(0).fulfillments()).hasSize(1);
        assertThat(r2.orders().get(0).fulfillments()).hasSize(2);
        assertThat(r3.orders().get(0).fulfillments()).hasSize(3);
        for (CheckoutResultResponse r : List.of(r1, r2, r3)) {
            long orderId = r.orders().get(0).orderId();
            BigDecimal itemsTotal = jdbc.queryForObject("select sum(subtotal) from dbo.order_items where order_id = ?",
                    BigDecimal.class, orderId);
            assertThat(shippingFee(orderId)).isEqualByComparingTo("30000");
            assertThat(total(orderId)).isEqualByComparingTo(itemsTotal.add(new BigDecimal("30000")));
            assertThat(r.totalAmount()).isEqualByComparingTo(total(orderId));
            assertThat(jdbc.queryForObject("select amount from dbo.payments where order_id = ?", BigDecimal.class,
                    orderId)).isEqualByComparingTo(total(orderId));
        }
    }

    @Test
    @DisplayName("BE03-F24 COD delivery from one Store: 30.000 once, amount to collect = items + fee")
    void f24_codOneStore() {
        Map<String, Long> lines = cart(ALICE, SERUM, 2);

        CheckoutResultResponse result = confirm(ALICE, lines.values(), FulfillmentType.DELIVERY, PaymentMethod.COD, null);

        long orderId = result.orders().get(0).orderId();
        assertThat(shippingFee(orderId)).isEqualByComparingTo("30000");
        assertThat(total(orderId)).isEqualByComparingTo(price(SERUM).multiply(BigDecimal.valueOf(2)).add(new BigDecimal("30000")));
        assertThat(jdbc.queryForObject("select count(*) from dbo.payments where order_id = ?", Long.class, orderId)).isZero();
    }

    @Test
    @DisplayName("BE03-F25/F26 COD split: every independent Order pays its own 30.000; session total = sum of Orders")
    void f25_f26_codSplitFeePerOrder() {
        Map<String, Long> two = cart(ALICE, SERUM, 1, SHAMPOO, 1);
        CheckoutResultResponse r2 = confirm(ALICE, two.values(), FulfillmentType.DELIVERY, PaymentMethod.COD, null);
        Map<String, Long> three = cart(ALICE, SERUM, 1, SHAMPOO, 1, SUN_MILK, 1);
        CheckoutResultResponse r3 = confirm(ALICE, three.values(), FulfillmentType.DELIVERY, PaymentMethod.COD, null);

        assertThat(r2.orders()).hasSize(2);
        assertThat(r3.orders()).hasSize(3);
        for (CheckoutResultResponse r : List.of(r2, r3)) {
            BigDecimal sum = BigDecimal.ZERO;
            for (OrderSummaryResponse order : r.orders()) {
                BigDecimal items = jdbc.queryForObject("select sum(subtotal) from dbo.order_items where order_id = ?",
                        BigDecimal.class, order.orderId());
                assertThat(shippingFee(order.orderId())).isEqualByComparingTo("30000");
                assertThat(total(order.orderId())).isEqualByComparingTo(items.add(new BigDecimal("30000")));
                assertThat(order.fulfillments()).hasSize(1);
                sum = sum.add(total(order.orderId()));
            }
            assertThat(r.totalAmount()).isEqualByComparingTo(sum);
        }
        // three Orders cost three fees, two cost two
        assertThat(r3.totalAmount().subtract(jdbc.queryForObject("select sum(oi.subtotal) from dbo.order_items oi "
                + "join dbo.orders o on o.order_id = oi.order_id where o.checkout_id = ?", BigDecimal.class,
                r3.checkoutId()))).isEqualByComparingTo("90000");
    }

    @Test
    @DisplayName("BE03-C5 COD split of one SKU over two Stores: two Orders, each with its units and its own fee")
    void c5_codSplitOfOneSku() {
        whipStock(Map.of("OS-THUDUC", 3, "OS-GOVAP", 2));
        Map<String, Long> lines = cart(ALICE, WHIP, 5);
        BigDecimal unit = price(WHIP);

        CheckoutResultResponse result = confirm(ALICE, lines.values(), FulfillmentType.DELIVERY, PaymentMethod.COD, null);

        assertThat(result.orders()).hasSize(2);
        assertThat(result.orders().get(0).totalAmount())
                .isEqualByComparingTo(unit.multiply(BigDecimal.valueOf(3)).add(new BigDecimal("30000")));
        assertThat(result.orders().get(1).totalAmount())
                .isEqualByComparingTo(unit.multiply(BigDecimal.valueOf(2)).add(new BigDecimal("30000")));
        assertThat(result.totalAmount()).isEqualByComparingTo(unit.multiply(BigDecimal.valueOf(5)).add(new BigDecimal("60000")));
        assertThat(jdbc.queryForObject("select count(*) from dbo.orders where checkout_id = ?", Long.class,
                result.checkoutId())).isEqualTo(2);
    }

    @Test
    @DisplayName("BE03-F27/F28 STORE_PICKUP is free for ONLINE and for PAY_AT_STORE")
    void f27_f28_pickupIsFree() {
        Map<String, Long> a = cart(ALICE, SERUM, 1);
        CheckoutResultResponse online = confirm(ALICE, a.values(), FulfillmentType.STORE_PICKUP, PaymentMethod.ONLINE,
                storeId("OS-THUDUC"));
        Map<String, Long> b = cart(ALICE, SERUM, 1);
        CheckoutResultResponse atStore = confirm(ALICE, b.values(), FulfillmentType.STORE_PICKUP,
                PaymentMethod.PAY_AT_STORE, storeId("OS-THUDUC"));

        for (CheckoutResultResponse r : List.of(online, atStore)) {
            long orderId = r.orders().get(0).orderId();
            assertThat(shippingFee(orderId)).isEqualByComparingTo("0");
            assertThat(total(orderId)).isEqualByComparingTo(price(SERUM));
        }
    }

    @Test
    @DisplayName("BE03-F30 the fee changed after the preview: refused and must be confirmed again")
    void f30_feeChangedAfterPreview() {
        Map<String, Long> lines = cart(ALICE, SERUM, 1);
        CheckoutPreviewResponse preview = preview(ALICE, lines.values(), FulfillmentType.DELIVERY, PaymentMethod.COD, null);
        long ordersBefore = count("orders");
        BigDecimal approved = checkoutProperties.getShippingFeePerOrder();
        try {
            checkoutProperties.setShippingFeePerOrder(new BigDecimal("45000"));
            assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, request(lines.values(), FulfillmentType.DELIVERY,
                    PaymentMethod.COD, null, HCM_ADDRESS, false, preview.plan().signature(), preview.grandTotal())))
                    .isInstanceOfSatisfying(CheckoutConflictException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(CheckoutConflictException.CODE_TOTAL_CHANGED));
            assertThat(count("orders")).isEqualTo(ordersBefore);
            // without an expected total the signature still catches it
            assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, request(lines.values(), FulfillmentType.DELIVERY,
                    PaymentMethod.COD, null, HCM_ADDRESS, false, preview.plan().signature(), null)))
                    .isInstanceOfSatisfying(CheckoutConflictException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(CheckoutConflictException.CODE_PLAN_CHANGED));
        } finally {
            checkoutProperties.setShippingFeePerOrder(approved);
        }
    }

    // ================================================================= fingerprint binding

    @Test
    @DisplayName("The fingerprint is bound to the customer and to the delivery address that were previewed")
    void fingerprintIsBoundToCustomerAndAddress() {
        Map<String, Long> alices = cart(ALICE, SERUM, 1);
        CheckoutPreviewResponse preview = preview(ALICE, alices.values(), FulfillmentType.DELIVERY, PaymentMethod.COD, null);
        String signature = preview.plan().signature();
        long ordersBefore = count("orders");

        // another address than the one previewed
        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, request(alices.values(), FulfillmentType.DELIVERY,
                PaymentMethod.COD, null, "99 Trần Hưng Đạo, Quận 5, TP. Hồ Chí Minh", false, signature, null)))
                .isInstanceOfSatisfying(CheckoutConflictException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(CheckoutConflictException.CODE_PLAN_CHANGED));
        // same lines, other customer: the signature of Alice is worthless for Bob
        Map<String, Long> bobs = cart(BOB, SERUM, 1);
        assertThatThrownBy(() -> checkoutService.placeOrder(BOB, request(bobs.values(), FulfillmentType.DELIVERY,
                PaymentMethod.COD, null, HCM_ADDRESS, false, signature, null)))
                .isInstanceOfSatisfying(CheckoutConflictException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(CheckoutConflictException.CODE_PLAN_CHANGED));
        assertThat(count("orders")).isEqualTo(ordersBefore);

        // the previewed address (spacing aside) is accepted
        CheckoutResultResponse ok = checkoutService.placeOrder(ALICE, request(alices.values(), FulfillmentType.DELIVERY,
                PaymentMethod.COD, null, "  " + HCM_ADDRESS.replace(", ", ",  ") + " ", false, signature, preview.grandTotal()));
        assertThat(ok.orders()).hasSize(1);
    }
}
