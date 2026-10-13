package com.oneshop;

import com.oneshop.dto.request.AddCartItemRequest;
import com.oneshop.dto.request.PlaceOrderRequest;
import com.oneshop.dto.response.CheckoutResultResponse;
import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.exception.BadRequestException;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.CartService;
import com.oneshop.service.CheckoutService;
import com.oneshop.service.InventoryService;
import com.oneshop.service.PaymentService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

/**
 * V3 checkout with REAL, separately committed SQL Server transactions: no overselling when customers race for the
 * last unit (one Store and several Stores), a double submit of one cart, a failure in the middle of a multi-Store
 * order (everything rolls back), a failing payment initialization (everything rolls back) and opposite lock orders.
 *
 * <p>These tests commit, so {@link SeedGuardV3} puts the test database back after each one; they only run against a
 * database whose name ends with {@code _test}. Every race has a timeout.
 *
 * <p>{@code oneshop.checkout.online-enabled=true} is a TEST-ONLY switch: ONLINE checkout is closed in the
 * application until BE-05.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=test-only-secret-test-only-secret-1234567890",
        "oneshop.checkout.online-enabled=true"})
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class CheckoutV3ConcurrencyDatabaseIntegrationTest {

    private static final String ALICE = "khachhang1@example.com";
    private static final String BOB = "khachhang2@example.com";

    @Autowired
    CheckoutService checkoutService;
    @Autowired
    CartService cartService;
    @MockitoSpyBean
    InventoryService inventoryService;
    @MockitoSpyBean
    PaymentService paymentService;
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
    private boolean remembered;

    @BeforeEach
    void remember() {
        guard = new SeedGuardV3(jdbc);
        assumeTrue(guard.isTestDatabase(), "these tests commit: run with DB_NAME=oneshop_test");
        guard.remember();
        remembered = true;
    }

    @AfterEach
    void restore() {
        if (remembered) {
            guard.restore();
        }
    }

    // ------------------------------------------------------------------ helpers

    private long productId(String sku) {
        return productRepository.findBySku(sku).orElseThrow().getId();
    }

    private long storeId(String code) {
        return storeRepository.findByCode(code).orElseThrow().getId();
    }

    private long sp(String sku, String storeCode) {
        return storeProductRepository.findByStoreIdAndProductId(storeId(storeCode), productId(sku)).orElseThrow().getId();
    }

    private int stock(long storeProductId) {
        return jdbc.queryForObject("select quantity from dbo.store_products where store_product_id = ?",
                Integer.class, storeProductId);
    }

    private void setStock(long storeProductId, int quantity) {
        jdbc.update("update dbo.store_products set quantity = ? where store_product_id = ?", quantity, storeProductId);
    }

    /** Adds a line (committed) and returns the cart item id. */
    private long add(String email, String sku, int quantity) {
        cartService.addItem(email, AddCartItemRequest.forProduct(productId(sku), quantity));
        return cartRepository.findByUserEmail(email).map(c -> cartItemRepository.findByCartIdOrderByIdAsc(c.getId()))
                .orElseThrow().stream().filter(line -> line.getProduct().getSku().equals(sku))
                .findFirst().orElseThrow().getId();
    }

    private void emptyCart(String email) {
        cartRepository.findByUserEmail(email).ifPresent(c -> cartItemRepository.deleteAll(cartItemRepository.findByCartId(c.getId())));
    }

    private long cartLines(String email) {
        return cartRepository.findByUserEmail(email).map(c -> cartItemRepository.countByCartId(c.getId())).orElse(0L);
    }

    private static PlaceOrderRequest pickup(List<Long> ids, long storeId) {
        return new PlaceOrderRequest(ids, FulfillmentType.STORE_PICKUP, PaymentMethod.PAY_AT_STORE, "Người Nhận",
                "0912345678", null, storeId, false, null, null);
    }

    private static PlaceOrderRequest onlineDelivery(List<Long> ids) {
        return new PlaceOrderRequest(ids, FulfillmentType.DELIVERY, PaymentMethod.ONLINE, "Người Nhận", "0912345678",
                "12 Đường Thử Nghiệm", null, false, null, null);
    }

    /** Runs the tasks at the same instant; each result is the checkout result or the exception it ended with. */
    private List<Object> race(List<Callable<CheckoutResultResponse>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<CheckoutResultResponse> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return task.call();
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

    // ================================================================= BE03-22 last unit

    @Test
    @DisplayName("BE03-22 two customers racing for the last unit of one Store: exactly one Order, stock 0 not -1")
    void be03_22_lastUnitOneStore() throws Exception {
        long cheek = sp("BBIA-CHEEK-06", "OS-THUDUC");
        long thuDuc = storeId("OS-THUDUC");

        for (int round = 1; round <= 3; round++) {
            setStock(cheek, 1);
            emptyCart(ALICE);
            emptyCart(BOB);
            long alicesLine = add(ALICE, "BBIA-CHEEK-06", 1);
            long bobsLine = add(BOB, "BBIA-CHEEK-06", 1);
            Map<String, Long> before = guard.counts();

            List<Object> outcomes = race(List.of(
                    () -> checkoutService.placeOrder(ALICE, pickup(List.of(alicesLine), thuDuc)),
                    () -> checkoutService.placeOrder(BOB, pickup(List.of(bobsLine), thuDuc))));

            List<Object> winners = outcomes.stream().filter(CheckoutResultResponse.class::isInstance).toList();
            List<Object> losers = outcomes.stream().filter(Exception.class::isInstance).toList();
            assertThat(winners).as("round " + round + ": " + outcomes).hasSize(1);
            assertThat(losers).hasSize(1);
            assertThat(losers.get(0)).isInstanceOf(BadRequestException.class);

            assertThat(stock(cheek)).as("round " + round).isZero();
            Map<String, Long> after = guard.counts();
            assertThat(after.get("orders") - before.get("orders")).isEqualTo(1);
            assertThat(after.get("order_fulfillments") - before.get("order_fulfillments")).isEqualTo(1);
            assertThat(after.get("order_items") - before.get("order_items")).isEqualTo(1);
            assertThat(after.get("inventory_movements") - before.get("inventory_movements")).isEqualTo(1);
            CheckoutResultResponse won = (CheckoutResultResponse) winners.get(0);
            Map<String, Object> movement = jdbc.queryForMap("select type, quantity_before, quantity_change, "
                    + "quantity_after from dbo.inventory_movements where reference_order_id = ?",
                    won.orders().get(0).orderId());
            assertThat(movement.get("type")).isEqualTo("ORDER");
            assertThat(movement.get("quantity_before")).isEqualTo(1);
            assertThat(movement.get("quantity_change")).isEqualTo(-1);
            assertThat(movement.get("quantity_after")).isEqualTo(0);

            // the winner's line left the cart, the loser's line is still there
            boolean aliceWon = outcomes.get(0) instanceof CheckoutResultResponse;
            assertThat(cartLines(ALICE)).isEqualTo(aliceWon ? 0 : 1);
            assertThat(cartLines(BOB)).isEqualTo(aliceWon ? 1 : 0);

            guard.restore();
            guard.remember();
        }
    }

    @Test
    @DisplayName("BE03-22b two multi-Store ONLINE checkouts racing for the last unit in two Stores: one Order wins")
    void be03_22b_lastUnitAcrossTwoStores() throws Exception {
        long serum = sp("COCOON-SERUM-30", "OS-THUDUC");
        long shampoo = sp("COCOON-SHAM-310", "OS-GOVAP");
        setStock(serum, 1);
        setStock(shampoo, 1);
        emptyCart(ALICE);
        emptyCart(BOB);
        List<Long> alices = List.of(add(ALICE, "COCOON-SERUM-30", 1), add(ALICE, "COCOON-SHAM-310", 1));
        List<Long> bobs = List.of(add(BOB, "COCOON-SHAM-310", 1), add(BOB, "COCOON-SERUM-30", 1));
        Map<String, Long> before = guard.counts();

        List<Object> outcomes = race(List.of(
                () -> checkoutService.placeOrder(ALICE, onlineDelivery(alices)),
                () -> checkoutService.placeOrder(BOB, onlineDelivery(bobs))));

        assertThat(outcomes.stream().filter(CheckoutResultResponse.class::isInstance)).as(outcomes.toString()).hasSize(1);
        assertThat(outcomes.stream().filter(BadRequestException.class::isInstance)).hasSize(1);
        assertThat(stock(serum)).isZero();
        assertThat(stock(shampoo)).isZero();
        Map<String, Long> after = guard.counts();
        assertThat(after.get("orders") - before.get("orders")).isEqualTo(1);
        assertThat(after.get("order_fulfillments") - before.get("order_fulfillments")).isEqualTo(2);
        assertThat(after.get("inventory_movements") - before.get("inventory_movements")).isEqualTo(2);
        assertThat(after.get("payments") - before.get("payments")).isEqualTo(1);
    }

    // ================================================================= same-SKU split under contention

    private void whipStock(Map<String, Integer> byStore) {
        for (String code : List.of("OS-THUDUC", "OS-GOVAP", "OS-QUAN7", "OS-QUAN10", "OS-HOANKIEM")) {
            jdbc.update("update dbo.store_products set quantity = ?, reserved_quantity = 0 where store_product_id = ?",
                    byStore.getOrDefault(code, 0), sp("SENKA-WHIP-120", code));
        }
    }

    @Test
    @DisplayName("BE03-F10 two customers need the same split SKU: exactly one gets the 3 + 2 units, no oversell")
    void f10_sameSkuSplitUnderContention() throws Exception {
        whipStock(Map.of("OS-THUDUC", 3, "OS-GOVAP", 2));
        emptyCart(ALICE);
        emptyCart(BOB);
        List<Long> alices = List.of(add(ALICE, "SENKA-WHIP-120", 5));
        List<Long> bobs = List.of(add(BOB, "SENKA-WHIP-120", 5));
        Map<String, Long> before = guard.counts();
        // each customer confirms the plan previewed for them (the fingerprint is bound to the customer)
        String alicesSignature = previewSignature(ALICE, alices);
        String bobsSignature = previewSignature(BOB, bobs);

        List<Object> outcomes = race(List.of(
                () -> checkoutService.placeOrder(ALICE, codSplit(alices, alicesSignature)),
                () -> checkoutService.placeOrder(BOB, codSplit(bobs, bobsSignature))));

        assertThat(outcomes.stream().filter(CheckoutResultResponse.class::isInstance)).as(outcomes.toString()).hasSize(1);
        assertThat(outcomes.stream().filter(BadRequestException.class::isInstance)).hasSize(1);
        assertThat(stock(sp("SENKA-WHIP-120", "OS-THUDUC"))).isZero();
        assertThat(stock(sp("SENKA-WHIP-120", "OS-GOVAP"))).isZero();
        Map<String, Long> after = guard.counts();
        assertThat(after.get("orders") - before.get("orders")).isEqualTo(2);      // one consented COD split = 2 Orders
        assertThat(after.get("order_fulfillments") - before.get("order_fulfillments")).isEqualTo(2);
        assertThat(after.get("order_items") - before.get("order_items")).isEqualTo(2);
        assertThat(after.get("inventory_movements") - before.get("inventory_movements")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select sum(quantity) from dbo.order_items where order_id > "
                + "(select max(order_id) - 2 from dbo.orders)", Integer.class)).isEqualTo(5);
    }

    private static PlaceOrderRequest codSplit(List<Long> ids, String signature) {
        return new PlaceOrderRequest(ids, FulfillmentType.DELIVERY, PaymentMethod.COD, "Người Nhận", "0912345678",
                "12 Đường Thử Nghiệm, Quận 1", null, true, signature, null);
    }

    private String previewSignature(String email, List<Long> lines) {
        return checkoutService.preview(email, lines, FulfillmentType.DELIVERY, PaymentMethod.COD, null,
                "12 Đường Thử Nghiệm, Quận 1").plan().signature();
    }

    // ================================================================= sources that change after the preview

    @Test
    @DisplayName("BE03-F10b a Store, a StoreProduct or a reservation changes after the cart: refused, nothing created")
    void f10b_sourcesChangeAfterTheCart() {
        long thuDuc = sp("COCOON-SERUM-30", "OS-THUDUC");
        emptyCart(ALICE);
        List<Long> lines = List.of(add(ALICE, "COCOON-SERUM-30", 2));
        Map<String, Long> before = guard.counts();

        // 1. the Store becomes INACTIVE
        jdbc.update("update dbo.stores set status = 'INACTIVE' where code = 'OS-THUDUC'");
        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, pickup(lines, storeId("OS-THUDUC"))))
                .isInstanceOf(BadRequestException.class);
        assertThat(guard.counts()).isEqualTo(before);
        jdbc.update("update dbo.stores set status = 'ACTIVE' where code = 'OS-THUDUC'");

        // 2. the StoreProduct becomes INACTIVE
        jdbc.update("update dbo.store_products set status = 'INACTIVE' where store_product_id = ?", thuDuc);
        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, pickup(lines, storeId("OS-THUDUC"))))
                .isInstanceOf(BadRequestException.class);
        assertThat(guard.counts()).isEqualTo(before);
        jdbc.update("update dbo.store_products set status = 'ACTIVE' where store_product_id = ?", thuDuc);

        // 3. a legacy reservation eats the stock the customer wanted
        int quantity = stock(thuDuc);
        jdbc.update("update dbo.store_products set reserved_quantity = ? where store_product_id = ?", quantity - 1, thuDuc);
        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, pickup(lines, storeId("OS-THUDUC"))))
                .isInstanceOf(BadRequestException.class);
        assertThat(guard.counts()).isEqualTo(before);
        assertThat(stock(thuDuc)).isEqualTo(quantity);
    }

    // ================================================================= COD split rollback

    @Test
    @DisplayName("BE03-F11 COD split: if the second independent Order fails, the first is rolled back too")
    void f11_codSplitRollsBackAllOrders() {
        long serum = sp("COCOON-SERUM-30", "OS-THUDUC");
        long shampoo = sp("COCOON-SHAM-310", "OS-GOVAP");
        int serumBefore = stock(serum);
        int shampooBefore = stock(shampoo);
        emptyCart(ALICE);
        List<Long> lines = List.of(add(ALICE, "COCOON-SERUM-30", 1), add(ALICE, "COCOON-SHAM-310", 1));
        Map<String, Long> before = guard.counts();
        String signature = previewSignature(ALICE, lines);

        // the first Order (Thu Duc) really deducts, the second one (Go Vap) fails
        doCallRealMethod().doThrow(new IllegalStateException("simulated failure of the second Order"))
                .when(inventoryService).deductForOrder(any(), anyInt(), any());

        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, codSplit(lines, signature)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("second Order");

        assertThat(guard.counts()).isEqualTo(before);
        assertThat(stock(serum)).isEqualTo(serumBefore);
        assertThat(stock(shampoo)).isEqualTo(shampooBefore);
        assertThat(cartLines(ALICE)).isEqualTo(2);
    }

    // ================================================================= BE03-21 duplicate submit

    @Test
    @DisplayName("BE03-21b the same customer submitting the same lines at once: one Order, one deduction")
    void be03_21b_doubleSubmitOfOneCart() throws Exception {
        long serum = sp("COCOON-SERUM-30", "OS-THUDUC");
        int before = stock(serum);
        emptyCart(ALICE);
        List<Long> lines = List.of(add(ALICE, "COCOON-SERUM-30", 2));
        Map<String, Long> counts = guard.counts();

        List<Object> outcomes = race(List.of(
                () -> checkoutService.placeOrder(ALICE, pickup(lines, storeId("OS-THUDUC"))),
                () -> checkoutService.placeOrder(ALICE, pickup(lines, storeId("OS-THUDUC"))),
                () -> checkoutService.placeOrder(ALICE, pickup(lines, storeId("OS-THUDUC")))));

        assertThat(outcomes.stream().filter(CheckoutResultResponse.class::isInstance)).as(outcomes.toString()).hasSize(1);
        assertThat(stock(serum)).isEqualTo(before - 2);
        assertThat(guard.counts().get("orders") - counts.get("orders")).isEqualTo(1);
        assertThat(guard.counts().get("inventory_movements") - counts.get("inventory_movements")).isEqualTo(1);
        assertThat(cartLines(ALICE)).isZero();
    }

    // ================================================================= BE03-13 / 30 rollback

    @Test
    @DisplayName("BE03-13 a failure after the first Store was deducted rolls everything back")
    void be03_13_failureInTheMiddleRollsBackAllStores() {
        long serum = sp("COCOON-SERUM-30", "OS-THUDUC");
        long shampoo = sp("COCOON-SHAM-310", "OS-GOVAP");
        int serumBefore = stock(serum);
        int shampooBefore = stock(shampoo);
        emptyCart(ALICE);
        List<Long> lines = List.of(add(ALICE, "COCOON-SERUM-30", 1), add(ALICE, "COCOON-SHAM-310", 1));
        Map<String, Long> before = guard.counts();

        // first Store deducts for real, the second one fails
        doCallRealMethod().doThrow(new IllegalStateException("simulated failure at Store B"))
                .when(inventoryService).deductForOrder(any(), anyInt(), any());

        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, onlineDelivery(lines)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Store B");

        assertThat(guard.counts()).isEqualTo(before);
        assertThat(stock(serum)).isEqualTo(serumBefore);
        assertThat(stock(shampoo)).isEqualTo(shampooBefore);
        assertThat(cartLines(ALICE)).isEqualTo(2);
    }

    @Test
    @DisplayName("BE03-30 a failing payment initialization rolls back Order, Fulfillments, stock and cart")
    void be03_30_paymentInitializationFailureRollsBack() {
        long serum = sp("COCOON-SERUM-30", "OS-THUDUC");
        int serumBefore = stock(serum);
        emptyCart(ALICE);
        List<Long> lines = List.of(add(ALICE, "COCOON-SERUM-30", 2));
        Map<String, Long> before = guard.counts();

        // initializeOnlineAttempt is MANDATORY-transactional: stub the spy behind the transaction proxy, otherwise
        // the stubbing call itself would run the transaction interceptor outside a transaction
        PaymentService spyBehindProxy = AopTestUtils.getUltimateTargetObject(paymentService);
        doThrow(new IllegalStateException("simulated payment initialization failure"))
                .when(spyBehindProxy).initializeOnlineAttempt(any());

        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, onlineDelivery(lines)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(guard.counts()).isEqualTo(before);
        assertThat(stock(serum)).isEqualTo(serumBefore);
        assertThat(cartLines(ALICE)).isEqualTo(1);
    }

    // ================================================================= lock ordering

    @Test
    @DisplayName("Customers buying the same SKUs in opposite order both succeed, without deadlock")
    void oppositeOrderDoesNotDeadlock() throws Exception {
        long serum = sp("COCOON-SERUM-30", "OS-THUDUC");
        long toner = sp("INNI-TONER-200", "OS-THUDUC");
        int serumBefore = stock(serum);
        int tonerBefore = stock(toner);
        long thuDuc = storeId("OS-THUDUC");

        for (int round = 1; round <= 3; round++) {
            emptyCart(ALICE);
            emptyCart(BOB);
            List<Long> alices = List.of(add(ALICE, "COCOON-SERUM-30", 1), add(ALICE, "INNI-TONER-200", 1));
            List<Long> bobs = List.of(add(BOB, "INNI-TONER-200", 1), add(BOB, "COCOON-SERUM-30", 1));

            List<Object> outcomes = race(List.of(
                    () -> checkoutService.placeOrder(ALICE, pickup(alices, thuDuc)),
                    () -> checkoutService.placeOrder(BOB, pickup(bobs, thuDuc))));

            assertThat(outcomes).as("round " + round + ": " + outcomes).allMatch(CheckoutResultResponse.class::isInstance);
            assertThat(stock(serum)).isEqualTo(serumBefore - 2 * round);
            assertThat(stock(toner)).isEqualTo(tonerBefore - 2 * round);
        }
    }
}
