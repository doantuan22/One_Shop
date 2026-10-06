package com.oneshop;

import com.oneshop.dto.request.AddCartItemRequest;
import com.oneshop.dto.request.CheckoutRequest;
import com.oneshop.dto.request.StoreGroupCheckoutRequest;
import com.oneshop.entity.*;
import com.oneshop.exception.BadRequestException;
import com.oneshop.repository.OrderStatusHistoryRepository;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real SQL Server transactions. Only newly checked-out Orders are mutated; all rows are restored per test. */
@SpringBootTest(properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class OrderStateDatabaseIntegrationTest {
    private static final String CUSTOMER = "khachhang1@example.com";
    private static final List<String> TABLES = List.of("checkout_sessions", "orders", "order_items", "payments",
            "inventory_movements", "order_status_history", "store_products", "cart_items", "carts");
    @Autowired private OrderService orders;
    @Autowired private PaymentService payments;
    @Autowired private CheckoutService checkout;
    @Autowired private CartService cart;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager entityManager;
    @MockitoSpyBean private OrderStatusHistoryRepository histories;
    @MockitoSpyBean private OrderTransitionPolicy policy;
    private SeedGuard guard;
    private Map<String, List<Map<String, Object>>> before;

    private Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();
        for (String table : TABLES) rows.put(table, jdbc.queryForList("select * from dbo." + table + " order by 1"));
        return rows;
    }

    @BeforeEach
    void remember() {
        before = snapshot();
        guard = new SeedGuard(jdbc);
        guard.remember();
    }

    @AfterEach
    void restore() {
        reset(histories, policy);
        guard.restore();
        assertThat(snapshot()).as("Exact cleanup, including original ids and timestamps").isEqualTo(before);
    }

    private long create(FulfillmentType type, PaymentMethod method) {
        long stockId = jdbc.queryForObject("select sp.store_product_id from dbo.store_products sp join dbo.products p "
                + "on p.product_id=sp.product_id where sp.store_id=1 and p.sku='COCOON-SERUM-30'", Long.class);
        cart.addItem(CUSTOMER, new AddCartItemRequest(stockId, 1));
        long line = jdbc.queryForObject("select ci.cart_item_id from dbo.cart_items ci join dbo.carts c on c.cart_id=ci.cart_id "
                + "join dbo.users u on u.user_id=c.user_id where u.email=? and ci.store_product_id=?", Long.class, CUSTOMER, stockId);
        var group = new StoreGroupCheckoutRequest(1L, type, method, "Kiểm thử trạng thái", "0912345678",
                type == FulfillmentType.DELIVERY ? "1 Đường kiểm thử" : null);
        return checkout.placeOrder(CUSTOMER, new CheckoutRequest(List.of(line), List.of(group))).orders().get(0).orderId();
    }

    private long confirmed(FulfillmentType type) {
        return create(type, type == FulfillmentType.DELIVERY ? PaymentMethod.COD : PaymentMethod.PAY_AT_STORE);
    }

    private List<Map<String, Object>> history(long id) {
        return jdbc.queryForList("select * from dbo.order_status_history where order_id=? order by history_id", id);
    }

    private String status(long id) {
        return jdbc.queryForObject("select order_status from dbo.orders where order_id=?", String.class, id);
    }

    @ParameterizedTest
    @EnumSource(FulfillmentType.class)
    void validTransitionHasOneHistoryActorTimestampNoteAndNoSideEffects(FulfillmentType type) {
        long id = confirmed(type);
        var stock = jdbc.queryForList("select * from dbo.store_products order by 1");
        var movements = jdbc.queryForList("select * from dbo.inventory_movements order by 1");
        var itemRows = jdbc.queryForList("select * from dbo.order_items where order_id=?", id);
        var orderBefore = jdbc.queryForMap("select * from dbo.orders where order_id=?", id);
        User actor = users.findByEmail("staff.thuduc@oneshop.vn").orElseThrow(); // Trusted test context, no HTTP actor input.
        orders.transition(id, OrderStatus.CONFIRMED, OrderStatus.PREPARING, actor, "Đã nhận xử lý");
        assertThat(status(id)).isEqualTo("PREPARING");
        var entries = history(id);
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0)).containsEntry("old_status", null).containsEntry("new_status", "CONFIRMED");
        assertThat(entries.get(1)).containsEntry("old_status", "CONFIRMED").containsEntry("new_status", "PREPARING")
                .containsEntry("changed_by_user_id", actor.getId()).containsEntry("note", "Đã nhận xử lý");
        assertThat(entries.get(1).get("changed_at")).isNotNull();
        var orderAfter = jdbc.queryForMap("select * from dbo.orders where order_id=?", id);
        orderBefore.remove("order_status"); orderAfter.remove("order_status");
        orderBefore.remove("updated_at"); orderAfter.remove("updated_at");
        assertThat(orderAfter).isEqualTo(orderBefore);
        assertThat(jdbc.queryForList("select * from dbo.store_products order by 1")).isEqualTo(stock);
        assertThat(jdbc.queryForList("select * from dbo.inventory_movements order by 1")).isEqualTo(movements);
        assertThat(jdbc.queryForList("select * from dbo.order_items where order_id=?", id)).isEqualTo(itemRows);
        assertThat(jdbc.queryForObject("select count(*) from dbo.payments where order_id=?", Integer.class, id)).isZero();
    }

    @ParameterizedTest
    @CsvSource({"DELIVERY,CONFIRMED,CONFIRMED", "DELIVERY,CONFIRMED,PACKED", "DELIVERY,PREPARING,CONFIRMED",
            "DELIVERY,PREPARING,READY_FOR_PICKUP", "STORE_PICKUP,PREPARING,PACKED", "STORE_PICKUP,PREPARING,SHIPPING",
            "DELIVERY,COMPLETED,CANCELLED", "DELIVERY,COMPLETED,PREPARING", "STORE_PICKUP,CANCELLED,CONFIRMED",
            "STORE_PICKUP,CANCELLED,PREPARING", "DELIVERY,CONFIRMED,CANCELLED"})
    void invalidSameBackwardSkipTerminalAndWrongBranchLeaveNoHistory(FulfillmentType type, OrderStatus from, OrderStatus target) {
        long id = confirmed(type);
        // Explicit test fixture state, never a production orchestration action.
        jdbc.update("update dbo.orders set order_status=? where order_id=?", from.name(), id);
        var rows = history(id);
        assertThatThrownBy(() -> orders.transition(id, from, target, null, null)).isInstanceOf(BadRequestException.class);
        assertThat(status(id)).isEqualTo(from.name());
        assertThat(history(id)).isEqualTo(rows);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void historyFailureRollsBackAlreadyFlushedOrderAndOptionalFlushedHistory(boolean flushHistory) {
        long id = confirmed(FulfillmentType.DELIVERY);
        var original = jdbc.queryForMap("select * from dbo.orders where order_id=?", id);
        var entries = history(id);
        doAnswer(call -> {
            assertThat(entityManager.createNativeQuery("select order_status from dbo.orders where order_id=" + id).getSingleResult())
                    .isEqualTo("PREPARING"); // The Order UPDATE really reached SQL inside the failing transaction.
            if (flushHistory) {
                entityManager.persist(call.getArgument(0));
                entityManager.flush();
            }
            throw new IllegalStateException("Injected audit failure");
        }).when(histories).save(any(OrderStatusHistory.class));
        assertThatThrownBy(() -> orders.transition(id, OrderStatus.CONFIRMED, OrderStatus.PREPARING, null, null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForMap("select * from dbo.orders where order_id=?", id)).isEqualTo(original);
        assertThat(history(id)).isEqualTo(entries);
    }

    @RepeatedTest(3)
    void concurrentDuplicateHasOneWinnerAndExactlyOneAudit() throws Exception {
        long id = confirmed(FulfillmentType.DELIVERY);
        var results = race(() -> orders.transition(id, OrderStatus.CONFIRMED, OrderStatus.PREPARING, null, "system"),
                () -> orders.transition(id, OrderStatus.CONFIRMED, OrderStatus.PREPARING, null, "system"));
        assertThat(results.stream().filter(Objects::isNull).count()).isEqualTo(1);
        assertThat(results.stream().filter(BadRequestException.class::isInstance).count()).isEqualTo(1);
        assertThat(status(id)).isEqualTo("PREPARING");
        assertThat(history(id)).hasSize(2);
        assertThat(history(id).get(1)).containsEntry("old_status", "CONFIRMED").containsEntry("new_status", "PREPARING")
                .containsEntry("changed_by_user_id", null);
    }

    @Test
    void packedVersusShippingCannotSkipFromStalePreparingIntent() throws Exception {
        long id = confirmed(FulfillmentType.DELIVERY);
        orders.transition(id, OrderStatus.CONFIRMED, OrderStatus.PREPARING, null, null);
        var results = race(() -> orders.transition(id, OrderStatus.PREPARING, OrderStatus.PACKED, null, null),
                () -> orders.transition(id, OrderStatus.PREPARING, OrderStatus.SHIPPING, null, null));
        assertThat(results.stream().filter(Objects::isNull).count()).isEqualTo(1);
        assertThat(results.stream().filter(BadRequestException.class::isInstance).count()).isEqualTo(1);
        assertThat(status(id)).isEqualTo("PACKED");
        assertThat(history(id)).hasSize(3);
        assertThat(history(id).get(2)).containsEntry("old_status", "PREPARING").containsEntry("new_status", "PACKED");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void paymentOutcomesUseCentralPolicyAndKeepSystemHistory(boolean success) {
        long id = create(FulfillmentType.STORE_PICKUP, PaymentMethod.ONLINE);
        var pending = payments.createOnlinePaymentAttempt(CUSTOMER, id);
        if (success) payments.markOnlinePaymentSuccess(CUSTOMER, id, pending.paymentId());
        else payments.markOnlinePaymentFailed(CUSTOMER, id, pending.paymentId());
        var target = success ? OrderStatus.CONFIRMED : OrderStatus.CANCELLED;
        verify(policy).canTransition(OrderStatus.PENDING_PAYMENT, target, FulfillmentType.STORE_PICKUP,
                success ? OrderTransitionPolicy.Cause.PAYMENT_SUCCESS : OrderTransitionPolicy.Cause.PAYMENT_FAILURE);
        assertThat(history(id)).hasSize(2);
        assertThat(history(id).get(1)).containsEntry("old_status", "PENDING_PAYMENT").containsEntry("new_status", target.name())
                .containsEntry("changed_by_user_id", null);
        assertThat(jdbc.queryForObject("select payment_status from dbo.orders where order_id=?", String.class, id))
                .isEqualTo(success ? "PAID" : "FAILED");
    }

    @Test
    void repeatedPaymentSuccessAfterPreparingDoesNotRepeatHistoryOrRewindOrder() {
        long id = create(FulfillmentType.DELIVERY, PaymentMethod.ONLINE);
        var pending = payments.createOnlinePaymentAttempt(CUSTOMER, id);
        var success = payments.markOnlinePaymentSuccess(CUSTOMER, id, pending.paymentId());
        orders.transition(id, OrderStatus.CONFIRMED, OrderStatus.PREPARING, null, null);
        var entries = history(id);
        assertThat(payments.markOnlinePaymentSuccess(CUSTOMER, id, pending.paymentId())).isEqualTo(success);
        assertThat(status(id)).isEqualTo("PREPARING");
        assertThat(history(id)).isEqualTo(entries);
    }

    @Test
    void noteLimitIsCheckedBeforeWritesAndNullSystemActorIsStored() {
        long id = confirmed(FulfillmentType.STORE_PICKUP);
        var original = jdbc.queryForMap("select * from dbo.orders where order_id=?", id);
        var entries = history(id);
        assertThatThrownBy(() -> orders.transition(id, OrderStatus.CONFIRMED, OrderStatus.PREPARING, null, "x".repeat(501)))
                .isInstanceOf(BadRequestException.class);
        assertThat(jdbc.queryForMap("select * from dbo.orders where order_id=?", id)).isEqualTo(original);
        assertThat(history(id)).isEqualTo(entries);
        orders.transition(id, OrderStatus.CONFIRMED, OrderStatus.PREPARING, null, "x".repeat(500));
        assertThat(history(id).get(1)).containsEntry("note", "x".repeat(500)).containsEntry("changed_by_user_id", null);
    }

    @Test
    void genericPrimitiveCannotUsePaymentEdgesAndPaymentHelpersRequireTransaction() {
        long id = create(FulfillmentType.DELIVERY, PaymentMethod.ONLINE);
        var original = history(id);
        for (var target : new OrderStatus[]{OrderStatus.CONFIRMED, OrderStatus.CANCELLED}) {
            assertThatThrownBy(() -> orders.transition(id, OrderStatus.PENDING_PAYMENT, target, null, null))
                    .isInstanceOf(BadRequestException.class);
        }
        assertThatThrownBy(() -> orders.confirmAfterOnlinePayment(new Order()))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThat(history(id)).isEqualTo(original);
        assertThat(status(id)).isEqualTo("PENDING_PAYMENT");
    }

    private List<Throwable> race(Runnable first, Runnable second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (Runnable action : List.of(first, second)) futures.add(pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Race start timeout");
                try { action.run(); return null; } catch (Throwable failure) { return failure; }
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Throwable> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
