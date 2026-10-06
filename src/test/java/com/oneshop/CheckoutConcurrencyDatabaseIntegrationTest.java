package com.oneshop;

import com.oneshop.dto.request.AddCartItemRequest;
import com.oneshop.dto.request.CheckoutRequest;
import com.oneshop.dto.request.StoreGroupCheckoutRequest;
import com.oneshop.dto.response.CheckoutResultResponse;
import com.oneshop.entity.CartItem;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doCallRealMethod;

/**
 * Phase 8 behaviour that only shows with real, separately committed transactions on SQL Server: two customers racing
 * for the last unit (TC-07), a failure in the middle of a multi-Store checkout (everything rolls back), the same
 * customer submitting twice, and lock ordering.
 *
 * <p>Skipped without a configured database. These tests commit, so {@link SeedGuard} records the state before each
 * test and puts it back afterwards.
 */
@SpringBootTest(properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class CheckoutConcurrencyDatabaseIntegrationTest {

    private static final String ALICE = "khachhang1@example.com";
    private static final String BOB = "khachhang2@example.com";

    @Autowired
    private CheckoutService checkoutService;

    @Autowired
    private CartService cartService;

    @MockitoSpyBean
    private InventoryService inventoryService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private StoreProductRepository storeProductRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private SeedGuard guard;

    @BeforeEach
    void remember() {
        guard = new SeedGuard(jdbc);
        guard.remember();
    }

    @AfterEach
    void restore() {
        guard.restore();
    }

    // ------------------------------------------------------------------ helpers

    private Long storeId(String code) {
        return storeRepository.findByCode(code).orElseThrow().getId();
    }

    private Long sp(String sku, String storeCode) {
        return storeProductRepository.findByStoreIdAndProductId(storeId(storeCode),
                productRepository.findBySku(sku).orElseThrow().getId()).orElseThrow().getId();
    }

    private int stock(Long storeProductId) {
        Integer value = jdbc.queryForObject("select quantity from dbo.store_products where store_product_id = ?",
                Integer.class, storeProductId);
        return value == null ? -1 : value;
    }

    private void setStock(Long storeProductId, int quantity) {
        jdbc.update("update dbo.store_products set quantity = ? where store_product_id = ?", quantity, storeProductId);
    }

    private Long add(String email, Long storeProductId, int quantity) {
        cartService.addItem(email, new AddCartItemRequest(storeProductId, quantity));
        return rowsOf(email).stream().filter(line -> line.getStoreProduct().getId().equals(storeProductId))
                .findFirst().orElseThrow().getId();
    }

    private List<CartItem> rowsOf(String email) {
        return cartRepository.findByUserEmail(email)
                .map(cart -> cartItemRepository.findByCartIdOrderByIdAsc(cart.getId())).orElseGet(List::of);
    }

    private Map<Long, String> cartSnapshot(String email) {
        return rowsOf(email).stream().collect(Collectors.toMap(CartItem::getId,
                line -> line.getStoreProduct().getId() + "x" + line.getQuantity()));
    }

    private static StoreGroupCheckoutRequest pickup(Long storeId) {
        return new StoreGroupCheckoutRequest(storeId, FulfillmentType.STORE_PICKUP, PaymentMethod.PAY_AT_STORE,
                "Người Nhận", "0912345678", null);
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

    // ================================================================= TC-07

    @Test
    void tc07_twoCustomersRacingForTheLastUnitNeverOversell() throws Exception {
        Long cheek = sp("BBIA-CHEEK-08", "OS-THUDUC");
        Long thuDuc = storeId("OS-THUDUC");

        for (int round = 1; round <= 5; round++) {
            Map<String, Long> before = guard.counts();
            setStock(cheek, 1);
            Long alicesLine = add(ALICE, cheek, 1);
            Long bobsLine = add(BOB, cheek, 1);
            Map<Long, String> bobsCartBefore = cartSnapshot(BOB);

            List<Object> outcomes = race(List.of(
                    () -> checkoutService.placeOrder(ALICE, new CheckoutRequest(List.of(alicesLine), List.of(pickup(thuDuc)))),
                    () -> checkoutService.placeOrder(BOB, new CheckoutRequest(List.of(bobsLine), List.of(pickup(thuDuc))))));

            // exactly one transaction committed; the other was told there is not enough stock
            List<Object> winners = outcomes.stream().filter(CheckoutResultResponse.class::isInstance).toList();
            List<Object> losers = outcomes.stream().filter(Exception.class::isInstance).toList();
            assertThat(winners).as("round " + round + ": " + outcomes).hasSize(1);
            assertThat(losers).hasSize(1);
            assertThat(losers.get(0)).isInstanceOf(BadRequestException.class);
            assertThat(((Exception) losers.get(0)).getMessage()).contains("hết hàng");

            // stock is 0, not -1; one Order, one item, one ORDER movement 1 -> 0
            assertThat(stock(cheek)).as("round " + round).isZero();
            Map<String, Long> after = guard.counts();
            assertThat(after.get("orders") - before.get("orders")).isEqualTo(1);
            assertThat(after.get("checkout_sessions") - before.get("checkout_sessions")).isEqualTo(1);
            assertThat(after.get("order_items") - before.get("order_items")).isEqualTo(1);
            assertThat(after.get("inventory_movements") - before.get("inventory_movements")).isEqualTo(1);
            CheckoutResultResponse won = (CheckoutResultResponse) winners.get(0);
            Map<String, Object> movement = jdbc.queryForMap("select quantity_before, quantity_change, quantity_after from "
                    + "dbo.inventory_movements where reference_order_id = ?", won.orders().get(0).orderId());
            assertThat(movement.get("quantity_before")).isEqualTo(1);
            assertThat(movement.get("quantity_change")).isEqualTo(-1);
            assertThat(movement.get("quantity_after")).isEqualTo(0);

            // the winner's line left the cart, the loser's cart is untouched
            boolean aliceWon = outcomes.get(0) instanceof CheckoutResultResponse;
            assertThat(rowsOf(ALICE)).hasSize(aliceWon ? 0 : 1);
            assertThat(cartSnapshot(BOB)).hasSize(aliceWon ? bobsCartBefore.size() : bobsCartBefore.size() - 1);

            guard.restore();
            guard.remember();
        }
    }

    @Test
    void tc07_whenStockCoversOnlyOneOfTwoLargerOrdersOnlyOneIsPlaced() throws Exception {
        Long cheek = sp("BBIA-CHEEK-08", "OS-THUDUC");
        Long thuDuc = storeId("OS-THUDUC");
        setStock(cheek, 3);
        Long alicesLine = add(ALICE, cheek, 2);
        Long bobsLine = add(BOB, cheek, 2);

        List<Object> outcomes = race(List.of(
                () -> checkoutService.placeOrder(ALICE, new CheckoutRequest(List.of(alicesLine), List.of(pickup(thuDuc)))),
                () -> checkoutService.placeOrder(BOB, new CheckoutRequest(List.of(bobsLine), List.of(pickup(thuDuc))))));

        assertThat(outcomes.stream().filter(CheckoutResultResponse.class::isInstance)).hasSize(1);
        assertThat(outcomes.stream().filter(BadRequestException.class::isInstance)).hasSize(1);
        assertThat(stock(cheek)).isEqualTo(1);
    }

    @Test
    void customersBuyingTheSameSkusInOppositeOrderBothSucceedWithoutDeadlock() throws Exception {
        Long serum = sp("COCOON-SERUM-30", "OS-THUDUC");
        Long cheek = sp("BBIA-CHEEK-08", "OS-THUDUC");
        Long thuDuc = storeId("OS-THUDUC");
        int serumStock = 50;
        int cheekStock = 50;
        setStock(serum, serumStock);
        setStock(cheek, cheekStock);

        for (int round = 1; round <= 5; round++) {
            // Alice's cart has serum then cheek, Bob's has cheek then serum
            List<Long> alicesLines = List.of(add(ALICE, serum, 1), add(ALICE, cheek, 1));
            List<Long> bobsLines = List.of(add(BOB, cheek, 1), add(BOB, serum, 1));

            List<Object> outcomes = race(List.of(
                    () -> checkoutService.placeOrder(ALICE, new CheckoutRequest(alicesLines, List.of(pickup(thuDuc)))),
                    () -> checkoutService.placeOrder(BOB, new CheckoutRequest(bobsLines, List.of(pickup(thuDuc))))));

            assertThat(outcomes).as("round " + round + ": " + outcomes).allMatch(CheckoutResultResponse.class::isInstance);
            assertThat(stock(serum)).isEqualTo(serumStock - 2 * round);
            assertThat(stock(cheek)).isEqualTo(cheekStock - 2 * round);
        }
    }

    // ================================================================= one customer, two submits

    @Test
    void theSameCheckoutSubmittedTwiceAtOnceIsPlacedOnce() throws Exception {
        Long serum = sp("COCOON-SERUM-30", "OS-THUDUC");
        Long thuDuc = storeId("OS-THUDUC");
        int stockBefore = stock(serum);
        Map<String, Long> before = guard.counts();
        Long line = add(ALICE, serum, 2);
        CheckoutRequest request = new CheckoutRequest(List.of(line), List.of(pickup(thuDuc)));

        List<Object> outcomes = race(List.of(
                () -> checkoutService.placeOrder(ALICE, request),
                () -> checkoutService.placeOrder(ALICE, request),
                () -> checkoutService.placeOrder(ALICE, request)));

        assertThat(outcomes.stream().filter(CheckoutResultResponse.class::isInstance)).hasSize(1);
        assertThat(outcomes.stream().filter(BadRequestException.class::isInstance)).hasSize(2);
        assertThat(guard.counts().get("orders") - before.get("orders")).isEqualTo(1);
        assertThat(stock(serum)).isEqualTo(stockBefore - 2);
        assertThat(rowsOf(ALICE)).isEmpty();
    }

    // ================================================================= atomic: a failure in the middle

    @Test
    void failureAfterTheFirstStoreGroupWasWrittenRollsBackTheWholeCheckout() {
        // Bob's seed cart: three lines at three Stores. The second stock deduction blows up, after the session, the
        // first Order, its item, its stock deduction and its movement have already been written in the transaction.
        doCallRealMethod()
                .doThrow(new IllegalStateException("simulated failure while writing the second Store group"))
                .when(inventoryService).deductForOrder(any(), anyInt(), any());
        Map<String, Long> countsBefore = guard.counts();
        Map<Long, String> cartBefore = cartSnapshot(BOB);
        Map<Long, Integer> stockBefore = cartBefore.values().stream().map(v -> Long.valueOf(v.split("x")[0]))
                .collect(Collectors.toMap(id -> id, this::stock));
        List<StoreGroupCheckoutRequest> groups = rowsOf(BOB).stream()
                .map(line -> line.getStoreProduct().getStore().getId()).distinct().map(
                        CheckoutConcurrencyDatabaseIntegrationTest::pickup).toList();
        assertThat(groups).hasSize(3);

        assertThatThrownBy(() -> checkoutService.placeOrder(BOB, new CheckoutRequest(new ArrayList<>(cartBefore.keySet()), groups)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("simulated failure");

        // nothing of the checkout survived: no session, no Order of the first Store, no item, movement or history
        assertThat(guard.counts()).isEqualTo(countsBefore);
        // the stock of the first Store, already deducted inside the transaction, is back
        stockBefore.forEach((storeProductId, quantity) -> assertThat(stock(storeProductId)).isEqualTo(quantity));
        // and the cart still has every line
        assertThat(cartSnapshot(BOB)).isEqualTo(cartBefore);
    }

    @Test
    void afterAFailedCheckoutTheSameCartCanBeCheckedOutSuccessfully() {
        doCallRealMethod()
                .doThrow(new IllegalStateException("simulated failure"))
                .doCallRealMethod()
                .when(inventoryService).deductForOrder(any(), anyInt(), any());
        Map<Long, String> cartBefore = cartSnapshot(BOB);
        List<StoreGroupCheckoutRequest> groups = rowsOf(BOB).stream()
                .map(line -> line.getStoreProduct().getStore().getId()).distinct().map(
                        CheckoutConcurrencyDatabaseIntegrationTest::pickup).toList();
        CheckoutRequest request = new CheckoutRequest(new ArrayList<>(cartBefore.keySet()), groups);
        Map<String, Long> countsBefore = guard.counts();

        assertThatThrownBy(() -> checkoutService.placeOrder(BOB, request)).isInstanceOf(IllegalStateException.class);
        CheckoutResultResponse second = checkoutService.placeOrder(BOB, request);

        assertThat(second.orders()).hasSize(3);
        assertThat(guard.counts().get("orders") - countsBefore.get("orders")).isEqualTo(3);
        assertThat(guard.counts().get("checkout_sessions") - countsBefore.get("checkout_sessions")).isEqualTo(1);
        assertThat(guard.counts().get("inventory_movements") - countsBefore.get("inventory_movements")).isEqualTo(3);
        assertThat(rowsOf(BOB)).isEmpty();
    }
}
