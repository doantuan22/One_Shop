package com.oneshop;

import com.oneshop.dto.request.*;
import com.oneshop.dto.response.DeliveryAction;
import com.oneshop.entity.*;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.OrderStatusHistoryRepository;
import com.oneshop.repository.PaymentRepository;
import com.oneshop.service.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Production services, SQL Server and Tomcat. Every committed fixture is restored and compared row-for-row. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class DeliveryDatabaseIntegrationTest {
    private static final String CUSTOMER = "khachhang1@example.com";
    private static final String STAFF = "staff.thuduc@oneshop.vn";
    private static final String ROOT = "/staff/orders/delivery";
    private static final List<String> TABLES = List.of("orders", "order_items", "payments", "order_status_history",
            "inventory_movements", "store_products", "checkout_sessions", "cart_items", "carts");
    @Autowired private DeliveryFulfillmentService delivery;
    @Autowired private PaymentService payments;
    @Autowired private CartService cart;
    @Autowired private CheckoutService checkout;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager entityManager;
    @MockitoSpyBean private PaymentRepository paymentRepository;
    @MockitoSpyBean private OrderStatusHistoryRepository histories;
    @MockitoSpyBean private OrderService stateMachine;
    @LocalServerPort private int port;
    private SeedGuard guard;
    private Map<String, List<Map<String, Object>>> before;
    private Map<String, List<Map<String, Object>>> scopeBefore;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : TABLES) result.put(table, jdbc.queryForList("select * from dbo." + table + " order by 1"));
        return result;
    }

    @BeforeEach
    void remember() { before = snapshot(); scopeBefore = scopeSnapshot(); guard = new SeedGuard(jdbc); guard.remember(); }

    @AfterEach
    void restore() {
        reset(paymentRepository, histories, stateMachine);
        guard.restore();
        assertThat(snapshot()).as("Exact cleanup of every touched row including timestamps").isEqualTo(before);
        assertThat(scopeSnapshot()).as("Assignment, Store and account fixture changes also restored").isEqualTo(scopeBefore);
    }

    private Map<String, List<Map<String, Object>>> scopeSnapshot() {
        return Map.of("assignments", jdbc.queryForList("select * from dbo.staff_store_assignments order by 1"),
                "stores", jdbc.queryForList("select * from dbo.stores order by 1"),
                "users", jdbc.queryForList("select user_id,role_id,email,status,created_at,updated_at from dbo.users order by 1"));
    }

    private long staffId() {
        return jdbc.queryForObject("select user_id from dbo.users where email=?", Long.class, STAFF);
    }

    private long create(long storeId, FulfillmentType type, PaymentMethod method, boolean multipleItems) {
        List<Long> lines = new ArrayList<>();
        for (String sku : multipleItems ? List.of("COCOON-SERUM-30", "BBIA-CHEEK-08")
                : List.of(storeId == 1 ? "COCOON-SERUM-30" : "INNI-TONER-200")) {
            long stock = jdbc.queryForObject("select sp.store_product_id from dbo.store_products sp join dbo.products p "
                    + "on p.product_id=sp.product_id where sp.store_id=? and p.sku=?", Long.class, storeId, sku);
            cart.addItem(CUSTOMER, new AddCartItemRequest(stock, 1));
            lines.add(jdbc.queryForObject("select ci.cart_item_id from dbo.cart_items ci join dbo.carts c on c.cart_id=ci.cart_id "
                    + "join dbo.users u on u.user_id=c.user_id where u.email=? and ci.store_product_id=?", Long.class, CUSTOMER, stock));
        }
        var group = new StoreGroupCheckoutRequest(storeId, type, method, "Người nhận kiểm thử", "0912345678",
                type == FulfillmentType.DELIVERY ? "1 Đường kiểm thử" : null);
        return checkout.placeOrder(CUSTOMER, new CheckoutRequest(lines, List.of(group))).orders().get(0).orderId();
    }

    private long cod() { return create(1, FulfillmentType.DELIVERY, PaymentMethod.COD, false); }
    private long online(boolean pay) {
        long id = create(1, FulfillmentType.DELIVERY, PaymentMethod.ONLINE, false);
        if (pay) {
            var attempt = payments.createOnlinePaymentAttempt(CUSTOMER, id);
            payments.markOnlinePaymentSuccess(CUSTOMER, id, attempt.paymentId());
        }
        return id;
    }

    private void invoke(String staff, long id, DeliveryAction action) {
        switch (action) {
            case PREPARE -> delivery.startPreparingDelivery(staff, id);
            case PACK -> delivery.markDeliveryPacked(staff, id);
            case SHIP -> delivery.markDeliveryShipping(staff, id);
            case COMPLETE -> delivery.completeDelivery(staff, id);
        }
    }

    private void shipping(long id) {
        delivery.startPreparingDelivery(STAFF, id);
        delivery.markDeliveryPacked(STAFF, id);
        delivery.markDeliveryShipping(STAFF, id);
    }

    private Map<String, Object> order(long id) { return jdbc.queryForMap("select * from dbo.orders where order_id=?", id); }
    private List<Map<String, Object>> receipts(long id) { return jdbc.queryForList("select * from dbo.payments where order_id=? order by payment_id", id); }
    private List<Map<String, Object>> history(long id) { return jdbc.queryForList("select * from dbo.order_status_history where order_id=? order by history_id", id); }

    private void state(long id, String status, String payment) {
        assertThat(order(id)).containsEntry("order_status", status).containsEntry("payment_status", payment);
    }

    private void audit(long id, int offset) {
        var entries = history(id);
        assertThat(entries).hasSize(offset + 4);
        int index = offset;
        for (var action : DeliveryAction.values()) {
            assertThat(entries.get(index++)).containsEntry("old_status", action.getExpectedStatus().name())
                    .containsEntry("new_status", action.getTargetStatus().name()).containsEntry("changed_by_user_id", staffId())
                    .containsEntry("note", action.getLabel());
            assertThat(entries.get(index - 1).get("changed_at")).isNotNull();
        }
    }

    @Test
    void tc09CodLifecyclePreservesInventorySnapshotsAndCreatesPaymentOnlyAtCompletion() {
        long id = create(1, FulfillmentType.DELIVERY, PaymentMethod.COD, true);
        var stock = jdbc.queryForList("select * from dbo.store_products order by 1");
        var movements = jdbc.queryForList("select * from dbo.inventory_movements order by 1");
        var items = jdbc.queryForList("select * from dbo.order_items where order_id=?", id);
        var checkoutBefore = jdbc.queryForList("select * from dbo.checkout_sessions order by 1");
        var amount = order(id).get("total_amount");
        state(id, "CONFIRMED", "UNPAID");
        assertThat(receipts(id)).isEmpty();
        for (var action : DeliveryAction.values()) {
            invoke(STAFF, id, action);
            state(id, action.getTargetStatus().name(), action == DeliveryAction.COMPLETE ? "PAID" : "UNPAID");
            assertThat(receipts(id)).hasSize(action == DeliveryAction.COMPLETE ? 1 : 0);
            assertThat(jdbc.queryForList("select * from dbo.store_products order by 1")).isEqualTo(stock);
            assertThat(jdbc.queryForList("select * from dbo.inventory_movements order by 1")).isEqualTo(movements);
            assertThat(jdbc.queryForList("select * from dbo.order_items where order_id=?", id)).isEqualTo(items);
            assertThat(jdbc.queryForList("select * from dbo.checkout_sessions order by 1")).isEqualTo(checkoutBefore);
        }
        audit(id, 1);
        var receipt = receipts(id).get(0);
        assertThat(receipt).containsEntry("order_id", id).containsEntry("method", "COD").containsEntry("status", "SUCCESS").containsEntry("amount", amount);
        assertThat(receipt.get("paid_at")).isNotNull();
        assertThat(receipt.get("created_at")).isNotNull();
        assertThat(receipt.get("transaction_code").toString()).matches("PAY-[0-9a-f-]{36}");
        var ended = snapshot();
        for (var action : DeliveryAction.values()) assertThatThrownBy(() -> invoke(STAFF, id, action)).isInstanceOf(BadRequestException.class);
        assertThat(snapshot()).isEqualTo(ended);
    }

    @Test
    void onlineDeliveryNeverRewritesItsReceiptAndKeepsInventoryAndItemSnapshots() {
        long id = online(true);
        var receipt = receipts(id);
        var stock = jdbc.queryForList("select * from dbo.store_products order by 1");
        var movements = jdbc.queryForList("select * from dbo.inventory_movements order by 1");
        var items = jdbc.queryForList("select * from dbo.order_items where order_id=?", id);
        for (var action : DeliveryAction.values()) {
            invoke(STAFF, id, action);
            state(id, action.getTargetStatus().name(), "PAID");
            assertThat(receipts(id)).isEqualTo(receipt);
            assertThat(jdbc.queryForList("select * from dbo.store_products order by 1")).isEqualTo(stock);
            assertThat(jdbc.queryForList("select * from dbo.inventory_movements order by 1")).isEqualTo(movements);
            assertThat(jdbc.queryForList("select * from dbo.order_items where order_id=?", id)).isEqualTo(items);
        }
        audit(id, 2);
        assertThat(history(id).get(1)).containsEntry("changed_by_user_id", null);
    }

    @ParameterizedTest
    @EnumSource(DeliveryAction.class)
    void onlinePendingCannotBeFulfilledOrConfirmedByStaff(DeliveryAction action) {
        long id = online(false);
        var original = snapshot();
        assertThatThrownBy(() -> invoke(STAFF, id, action)).isInstanceOf(BadRequestException.class);
        assertThat(snapshot()).isEqualTo(original);
    }

    @ParameterizedTest
    @EnumSource(DeliveryAction.class)
    void inconsistentOnlineUnpaidAtAnyStageIsRejected(DeliveryAction action) {
        long id = online(false);
        jdbc.update("update dbo.orders set order_status=? where order_id=?", action.getExpectedStatus().name(), id);
        var original = snapshot();
        assertThatThrownBy(() -> invoke(STAFF, id, action)).isInstanceOf(BadRequestException.class);
        assertThat(snapshot()).isEqualTo(original);
    }

    @ParameterizedTest
    @EnumSource(DeliveryAction.class)
    void codPaidBeforeCompletionCannotBeFulfilledOrCollectedAgain(DeliveryAction action) {
        long id = cod();
        jdbc.update("update dbo.orders set order_status=?,payment_status='PAID' where order_id=?", action.getExpectedStatus().name(), id);
        var original = snapshot();
        assertThatThrownBy(() -> invoke(STAFF, id, action)).isInstanceOf(BadRequestException.class).hasMessageContaining("COD");
        assertThat(snapshot()).isEqualTo(original);
    }

    @Test
    void detailAndCodCollectionUsePurchaseSnapshotsAfterCatalogNameAndPriceChange() {
        long id = cod();
        var item = jdbc.queryForMap("select * from dbo.order_items where order_id=?", id);
        long stock = ((Number) item.get("store_product_id")).longValue();
        long product = jdbc.queryForObject("select product_id from dbo.store_products where store_product_id=?", Long.class, stock);
        String oldName = jdbc.queryForObject("select name from dbo.products where product_id=?", String.class, product);
        Object total = order(id).get("total_amount");
        try {
            jdbc.update("update dbo.products set name='Changed catalog fixture' where product_id=?", product);
            jdbc.update("update dbo.store_products set price=1 where store_product_id=?", stock);
            var detail = delivery.getDeliveryOrder(STAFF, id);
            assertThat(detail.items()).singleElement().satisfies(line -> {
                assertThat(line.productName()).isEqualTo(item.get("product_name"));
                assertThat(line.unitPrice()).isEqualTo(item.get("unit_price"));
            });
            shipping(id); delivery.completeDelivery(STAFF, id);
            assertThat(receipts(id).get(0)).containsEntry("amount", total);
            assertThat(jdbc.queryForMap("select * from dbo.order_items where order_id=?", id)).isEqualTo(item);
        } finally { jdbc.update("update dbo.products set name=? where product_id=?", oldName, product); }
    }

    @ParameterizedTest
    @EnumSource(DeliveryAction.class)
    void storeScopeIsRecheckedInServiceForEveryAction(DeliveryAction action) {
        long id = create(2, FulfillmentType.DELIVERY, PaymentMethod.COD, false);
        var original = snapshot();
        assertThatThrownBy(() -> delivery.getDeliveryOrder(STAFF, id)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> invoke(STAFF, id, action)).isInstanceOf(AccessDeniedException.class);
        assertThat(snapshot()).isEqualTo(original);
    }

    @ParameterizedTest
    @EnumSource(DeliveryAction.class)
    void pickupOrderCannotUseAnyDeliveryAction(DeliveryAction action) {
        long id = create(1, FulfillmentType.STORE_PICKUP, PaymentMethod.PAY_AT_STORE, false);
        var original = snapshot();
        assertThatThrownBy(() -> invoke(STAFF, id, action)).isInstanceOf(BadRequestException.class).hasMessageContaining("chỉ dành");
        assertThat(snapshot()).isEqualTo(original);
    }

    @ParameterizedTest
    @CsvSource({"CONFIRMED,PACK", "CONFIRMED,SHIP", "CONFIRMED,COMPLETE", "PREPARING,SHIP", "PREPARING,COMPLETE",
            "PACKED,COMPLETE", "SHIPPING,PREPARE", "PREPARING,PREPARE", "PACKED,PACK", "SHIPPING,SHIP"})
    void skipBackwardAndDuplicateActionsCannotWrite(OrderStatus current, DeliveryAction action) {
        long id = cod();
        jdbc.update("update dbo.orders set order_status=? where order_id=?", current.name(), id);
        var original = snapshot();
        assertThatThrownBy(() -> invoke(STAFF, id, action)).isInstanceOf(BadRequestException.class);
        assertThat(snapshot()).isEqualTo(original);
    }

    @Test
    void cancelledOnlineOrderCannotRunAnyDeliveryAction() {
        long id = online(false);
        var attempt = payments.createOnlinePaymentAttempt(CUSTOMER, id);
        payments.markOnlinePaymentFailed(CUSTOMER, id, attempt.paymentId());
        var cancelled = snapshot();
        for (var action : DeliveryAction.values()) assertThatThrownBy(() -> invoke(STAFF, id, action)).isInstanceOf(BadRequestException.class);
        assertThat(snapshot()).isEqualTo(cancelled);
    }

    @Test
    void inconsistentSuccessfulCodReceiptCannotBeCollectedAgain() {
        long id = cod();
        shipping(id);
        jdbc.update("insert into dbo.payments(order_id,method,amount,status,transaction_code,paid_at) "
                + "select order_id,'COD',total_amount,'SUCCESS','PAY-existing-fixture',SYSDATETIME() from dbo.orders where order_id=?", id);
        var original = snapshot();
        assertThatThrownBy(() -> delivery.completeDelivery(STAFF, id)).isInstanceOf(BadRequestException.class).hasMessageContaining("đã có thanh toán");
        assertThat(snapshot()).isEqualTo(original);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAYMENT_SAVE", "AFTER_PAYMENT_FLUSH", "BEFORE_TRANSITION", "HISTORY_SAVE", "AFTER_HISTORY_FLUSH"})
    void codCompletionRollsBackEveryWriteAtAllFailurePoints(String point) {
        long id = cod();
        shipping(id);
        var original = snapshot();
        if (point.equals("PAYMENT_SAVE")) doThrow(new IllegalStateException("Injected Payment save failure")).when(paymentRepository).saveAndFlush(any(Payment.class));
        if (point.equals("AFTER_PAYMENT_FLUSH")) doAnswer(call -> {
            entityManager.persist(call.getArgument(0)); entityManager.flush();
            assertPaidShippingInCurrentTransaction(id);
            throw new IllegalStateException("Injected after Payment flush");
        }).when(paymentRepository).saveAndFlush(any(Payment.class));
        if (point.equals("BEFORE_TRANSITION")) doAnswer(call -> {
            assertPaidShippingInCurrentTransaction(id);
            throw new IllegalStateException("Injected before completion transition");
        }).when(stateMachine).transition(eq(id), eq(OrderStatus.SHIPPING), eq(OrderStatus.COMPLETED), any(User.class), anyString());
        if (point.equals("HISTORY_SAVE") || point.equals("AFTER_HISTORY_FLUSH")) doAnswer(call -> {
            assertThat(entityManager.createNativeQuery("select order_status from dbo.orders where order_id=" + id).getSingleResult()).isEqualTo("COMPLETED");
            if (point.equals("AFTER_HISTORY_FLUSH")) { entityManager.persist(call.getArgument(0)); entityManager.flush(); }
            throw new IllegalStateException("Injected completion history failure");
        }).when(histories).save(any(OrderStatusHistory.class));
        assertThatThrownBy(() -> delivery.completeDelivery(STAFF, id)).isInstanceOf(IllegalStateException.class);
        state(id, "SHIPPING", "UNPAID");
        assertThat(receipts(id)).isEmpty();
        assertThat(snapshot()).isEqualTo(original);
    }

    private void assertPaidShippingInCurrentTransaction(long id) {
        assertThat(entityManager.createNativeQuery("select payment_status from dbo.orders where order_id=" + id).getSingleResult()).isEqualTo("PAID");
        assertThat(entityManager.createNativeQuery("select order_status from dbo.orders where order_id=" + id).getSingleResult()).isEqualTo("SHIPPING");
        assertThat(((Number) entityManager.createNativeQuery("select count(*) from dbo.payments where order_id=" + id + " and status='SUCCESS'").getSingleResult()).intValue()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"PREPARE,1", "PREPARE,2", "PREPARE,3", "SHIP,1", "SHIP,2", "SHIP,3", "COMPLETE,1", "COMPLETE,2", "COMPLETE,3"})
    void realSqlServerConcurrentRequestsProduceOneTransitionAndAtMostOneCollection(DeliveryAction action, int round) throws Exception {
        long id = cod();
        if (action != DeliveryAction.PREPARE) { delivery.startPreparingDelivery(STAFF, id); delivery.markDeliveryPacked(STAFF, id); }
        if (action == DeliveryAction.COMPLETE) delivery.markDeliveryShipping(STAFF, id);
        int initialHistory = history(id).size();
        var results = race(() -> invoke(STAFF, id, action), () -> invoke(STAFF, id, action));
        assertThat(results.stream().filter(Objects::isNull).count()).isEqualTo(1);
        assertThat(results.stream().filter(BadRequestException.class::isInstance).count()).isEqualTo(1);
        state(id, action.getTargetStatus().name(), action == DeliveryAction.COMPLETE ? "PAID" : "UNPAID");
        assertThat(history(id)).hasSize(initialHistory + 1);
        assertThat(receipts(id)).hasSize(action == DeliveryAction.COMPLETE ? 1 : 0);
        var original = snapshot();
        assertThatThrownBy(() -> invoke(STAFF, id, action)).isInstanceOf(BadRequestException.class);
        assertThat(snapshot()).isEqualTo(original); // Duplicate also keeps paid_at/code unchanged.
    }

    @ParameterizedTest
    @ValueSource(strings = {"ASSIGNMENT", "STORE", "USER"})
    void inactiveAssignmentStoreOrStaffRevokesReadAndMutationScope(String inactive) {
        long id = cod();
        long staff = staffId();
        String sql = switch (inactive) {
            case "ASSIGNMENT" -> "update dbo.staff_store_assignments set status=? where user_id=" + staff + " and store_id=1";
            case "STORE" -> "update dbo.stores set status=? where store_id=1";
            default -> "update dbo.users set status=? where user_id=" + staff;
        };
        String originalStatus = jdbc.queryForObject(switch (inactive) {
            case "ASSIGNMENT" -> "select status from dbo.staff_store_assignments where user_id=" + staff + " and store_id=1";
            case "STORE" -> "select status from dbo.stores where store_id=1";
            default -> "select status from dbo.users where user_id=" + staff;
        }, String.class);
        try {
            jdbc.update(sql, "INACTIVE");
            var original = snapshot();
            assertThatThrownBy(() -> delivery.getDeliveryOrders(STAFF)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> delivery.getDeliveryOrder(STAFF, id)).isInstanceOf(AccessDeniedException.class);
            for (var action : DeliveryAction.values()) assertThatThrownBy(() -> invoke(STAFF, id, action)).isInstanceOf(AccessDeniedException.class);
            assertThat(snapshot()).isEqualTo(original);
        } finally { jdbc.update(sql, originalStatus); }
    }

    @Test
    void readScopeSupportsMultipleAssignmentsAndOnlyDeliverySnapshots() {
        long own = cod();
        long other = create(2, FulfillmentType.DELIVERY, PaymentMethod.COD, false);
        long pickup = create(1, FulfillmentType.STORE_PICKUP, PaymentMethod.PAY_AT_STORE, false);
        var original = snapshot();
        assertThat(delivery.getDeliveryOrders(STAFF)).allSatisfy(row -> {
            assertThat(row.storeId()).isEqualTo(1L); assertThat(row.fulfillmentType()).isEqualTo(FulfillmentType.DELIVERY);
        });
        assertThat(delivery.getDeliveryOrders(STAFF).stream().map(row -> row.orderId()).toList()).contains(own).doesNotContain(other, pickup);
        assertThat(delivery.getDeliveryOrder(STAFF, own).items()).isNotEmpty();
        assertThat(snapshot()).isEqualTo(original); // GET/read operations do not write.
        long staff = staffId();
        jdbc.update("insert into dbo.staff_store_assignments(user_id,store_id,status) values(?,2,'ACTIVE')", staff);
        try {
            assertThat(delivery.getDeliveryOrders(STAFF).stream().map(row -> row.orderId()).toList()).contains(own, other).doesNotContain(pickup);
            assertThat(delivery.getDeliveryOrder(STAFF, other).order().storeId()).isEqualTo(2L);
        } finally { jdbc.update("delete from dbo.staff_store_assignments where user_id=? and store_id=2", staff); }
    }

    @ParameterizedTest
    @EnumSource(value = PaymentMethod.class, names = {"COD", "ONLINE"})
    void realHttpStaffCanRunFullFlowWithSnapshotsAndForgedFieldsIgnored(PaymentMethod method) throws Exception {
        long id = method == PaymentMethod.COD ? cod() : online(true);
        String cookie = login(STAFF);
        var stock = jdbc.queryForList("select * from dbo.store_products order by 1");
        var movements = jdbc.queryForList("select * from dbo.inventory_movements order by 1");
        var onlineReceipt = receipts(id);
        var list = request(ROOT + "?storeId=999", null, "Cookie", cookie);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains("data-order-id=\"" + id + "\"", "data-layout=\"staff\"");
        for (var action : DeliveryAction.values()) {
            var page = request(ROOT + "/" + id, null, "Cookie", cookie);
            assertThat(page.statusCode()).isEqualTo(200);
            assertThat(page.body()).contains(ROOT + "/" + id + "/" + action.getPath(), "Người nhận kiểm thử", "name=\"_csrf\"");
            assertThat(postAs(cookie, ROOT + "/" + id + "/" + action.getPath(),
                    "storeId=2&userId=1&staffEmail=admin&amount=1&method=PAY_AT_STORE&targetStatus=CANCELLED&transactionCode=HACK").statusCode()).isEqualTo(302);
            assertThat(postAs(cookie, ROOT + "/" + id + "/" + action.getPath(), "").statusCode()).isEqualTo(400);
        }
        state(id, "COMPLETED", "PAID");
        audit(id, method == PaymentMethod.COD ? 1 : 2);
        assertThat(receipts(id)).hasSize(1);
        if (method == PaymentMethod.ONLINE) assertThat(receipts(id)).isEqualTo(onlineReceipt);
        else assertThat(receipts(id).get(0)).containsEntry("method", "COD").containsEntry("amount", order(id).get("total_amount"));
        assertThat(jdbc.queryForList("select * from dbo.store_products order by 1")).isEqualTo(stock);
        assertThat(jdbc.queryForList("select * from dbo.inventory_movements order by 1")).isEqualTo(movements);
        assertThat(request(ROOT + "/" + id, null, "Cookie", cookie).body()).contains("PAY-", "Hoàn tất", "Đã thanh toán");
    }

    @Test
    void realHttpRejectsCrossStorePickupPendingMissingCsrfAndGetMutations() throws Exception {
        long other = create(2, FulfillmentType.DELIVERY, PaymentMethod.COD, false);
        long pickup = create(1, FulfillmentType.STORE_PICKUP, PaymentMethod.PAY_AT_STORE, false);
        long pending = online(false);
        long own = cod();
        var original = snapshot();
        String staff = login(STAFF);
        assertThat(request(ROOT + "/" + other, null, "Cookie", staff).statusCode()).isEqualTo(404);
        for (var action : DeliveryAction.values()) {
            String suffix = "/" + action.getPath();
            assertThat(postAs(staff, ROOT + "/" + other + suffix, "storeId=1").statusCode()).isEqualTo(403);
            assertThat(postAs(staff, ROOT + "/" + pickup + suffix, "").statusCode()).isEqualTo(400);
            assertThat(postAs(staff, ROOT + "/" + pending + suffix, "").statusCode()).isEqualTo(400);
            assertThat(request(ROOT + "/" + own + suffix, "", "Cookie", staff).statusCode()).isEqualTo(403);
            assertThat(request(ROOT + "/" + own + suffix, "", "Authorization", "Bearer " + staff.substring("ONESHOP_TOKEN=".length())).statusCode()).isEqualTo(403);
            assertThat(request(ROOT + "/" + own + suffix, null, "Cookie", staff).statusCode()).isEqualTo(405);
        }
        assertThat(snapshot()).isEqualTo(original);
    }

    @Test
    void codCollectionHelperRequiresSurroundingTransaction() {
        assertThatThrownBy(() -> payments.recordCodCollected(new Order()))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
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
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            List<Throwable> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally { start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    private HttpResponse<String> request(String path, String body, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (body == null) request.GET(); else request.header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (headers.length > 0) request.headers(headers);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String login(String email) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\"OneShop@123\"}"));
        var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        Matcher token = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(response.body());
        assertThat(token.find()).isTrue(); return "ONESHOP_TOKEN=" + token.group(1);
    }

    private HttpResponse<String> postAs(String cookie, String path, String body) throws Exception {
        var page = request("/login", null);
        Matcher csrf = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertThat(csrf.find()).isTrue();
        String tokenCookie = page.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN="))
                .map(c -> c.split(";")[0]).findFirst().orElseThrow();
        return request(path, body + "&_csrf=" + csrf.group(1), "Cookie", cookie + "; " + tokenCookie);
    }
}
