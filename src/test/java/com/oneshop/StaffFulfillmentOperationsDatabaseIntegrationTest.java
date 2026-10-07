package com.oneshop;

import com.oneshop.dto.request.*;
import com.oneshop.dto.response.*;
import com.oneshop.entity.*;
import com.oneshop.exception.BadRequestException;
import com.oneshop.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/** Real HTTP/JWT/CSRF/SiteMesh and SQL Server, including the new orchestration transaction; no mocked rules. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class StaffFulfillmentOperationsDatabaseIntegrationTest {
    private static final String STAFF = "staff.thuduc@oneshop.vn", CUSTOMER = Phase9IntegrationScenario.CUSTOMER;
    private static final String FORGED = "store_id=2&storeId=2&staffEmail=staff.govap%40oneshop.vn&userId=6"
            + "&orderStatus=COMPLETED&paymentStatus=PAID&amount=1&paymentMethod=ONLINE&targetStatus=COMPLETED";
    @Autowired StaffOperationsService operations;
    @Autowired CartService cart;
    @Autowired CheckoutService checkout;
    @Autowired PaymentService payments;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @LocalServerPort int port;
    private Phase9IntegrationScenario scenario;
    private Phase9IntegrationScenario.Cleanup cleanup;

    @BeforeEach void remember() {
        SecurityContextHolder.clearContext();
        scenario = new Phase9IntegrationScenario(jdbc, "http://localhost:" + port); cleanup = scenario.cleanup();
    }
    @AfterEach void restore() { SecurityContextHolder.clearContext(); cleanup.close(); }
    private <T> T as(Supplier<T> action) {
        var previous = SecurityContextHolder.getContext(); var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(STAFF, null,
                List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
        SecurityContextHolder.setContext(context);
        try { return action.get(); } finally { SecurityContextHolder.setContext(previous); }
    }
    private void delivery(long id, DeliveryAction action) { as(() -> { operations.performDelivery(id, action); return null; }); }
    private void pickup(long id, PickupAction action, String code) { as(() -> { operations.performPickup(id, action, code); return null; }); }
    private long scalar(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    private long create(long store, FulfillmentType type, PaymentMethod method) {
        long stock = scalar("select sp.store_product_id from dbo.store_products sp join dbo.products p on p.product_id=sp.product_id where sp.store_id=? and p.sku=?",
                store, store == 1 ? "COCOON-SERUM-30" : "INNI-TONER-200");
        cart.addItem(CUSTOMER, new AddCartItemRequest(stock, 1));
        long line = scalar("select ci.cart_item_id from dbo.cart_items ci join dbo.carts c on c.cart_id=ci.cart_id join dbo.users u on u.user_id=c.user_id where u.email=? and ci.store_product_id=?", CUSTOMER, stock);
        cart.updateItemQuantity(CUSTOMER, line, 1);
        return checkout.placeOrder(CUSTOMER, new CheckoutRequest(List.of(line), List.of(new StoreGroupCheckoutRequest(
                store, type, method, "Phase10 operations receiver", "0912345678", type == FulfillmentType.DELIVERY ? "10 Operations Street" : null)))).orders().get(0).orderId();
    }
    private static String path(long id, String flow, String action) { return "/staff/orders/" + id + "/" + flow + "/" + action; }
    private void state(long id, String status, String payment) {
        assertThat(scenario.order(id)).containsEntry("order_status", status).containsEntry("payment_status", payment);
    }
    private void pay(long id) {
        var attempt = payments.createOnlinePaymentAttempt(CUSTOMER, id);
        payments.markOnlinePaymentSuccess(CUSTOMER, id, attempt.paymentId());
    }
    private void httpAction(long id, String flow, String action, String body) throws Exception {
        var before = scenario.snapshot();
        var response = scenario.post(scenario.account(STAFF), path(id, flow, action), body);
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElseThrow()).endsWith("/staff/orders/" + id);
        // Fulfillment never touches inventory/cart/checkout/item snapshots, including when collection occurs.
        var after = scenario.snapshot();
        for (String table : List.of("store_products", "inventory_movements", "cart_items", "carts", "checkout_sessions", "order_items"))
            assertThat(after.get(table)).as(table).isEqualTo(before.get(table));
    }
    private void assertUi(long id, String flow, String next) throws Exception {
        var response = scenario.get("/staff/orders/" + id, scenario.account(STAFF));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("data-layout=\"staff\"", "id=\"fulfillment-action\"");
        for (String f : List.of("delivery", "pickup")) for (String action : List.of("prepare", "pack", "ship", "ready", "complete")) {
            String actionPath = path(id, f, action);
            if (f.equals(flow) && action.equals(next)) assertThat(response.body()).contains("action=\"" + actionPath + "\"", "name=\"_csrf\"");
            else assertThat(response.body()).doesNotContain("action=\"" + actionPath + "\"");
        }
        assertThat(response.body()).doesNotContain("id=\"pickup-code\"");
        if (scenario.code(id) != null) assertThat(response.body()).doesNotContain(scenario.code(id));
    }
    private void timeline(long id, List<String> statuses, int systemEntries) {
        var history = scenario.history(id); assertThat(history).hasSize(statuses.size());
        long actor = scalar("select user_id from dbo.users where email=?", STAFF);
        for (int i = 0; i < statuses.size(); i++) {
            assertThat(history.get(i)).containsEntry("old_status", i == 0 ? null : statuses.get(i - 1))
                    .containsEntry("new_status", statuses.get(i)).containsEntry("changed_by_user_id", i < systemEntries ? null : actor);
            assertThat(history.get(i).get("changed_at")).isNotNull();
            if (i > 0) assertThat(((java.sql.Timestamp) history.get(i).get("changed_at")).toLocalDateTime())
                    .isAfterOrEqualTo(((java.sql.Timestamp) history.get(i - 1).get("changed_at")).toLocalDateTime());
        }
    }
    private void receipt(long id, PaymentMethod method) {
        var receipts = scenario.receipts(id); assertThat(receipts).hasSize(1);
        assertThat(receipts.get(0)).containsEntry("method", method.name()).containsEntry("status", "SUCCESS")
                .containsEntry("amount", scenario.order(id).get("total_amount"));
        assertThat(receipts.get(0).get("paid_at")).isNotNull();
        assertThat(receipts.get(0).get("transaction_code").toString()).matches("PAY-[0-9a-f-]{36}");
    }

    @ParameterizedTest @EnumSource(value = PaymentMethod.class, names = {"COD", "ONLINE"})
    void deliveryHttpUiFollowsEveryStateAndUsesExistingPaymentHistoryRules(PaymentMethod method) throws Exception {
        long id = create(1, FulfillmentType.DELIVERY, method);
        if (method == PaymentMethod.ONLINE) {
            assertUi(id, "delivery", null); var before = scenario.snapshot();
            assertThat(scenario.post(scenario.account(STAFF), path(id, "delivery", "prepare"), FORGED).statusCode()).isEqualTo(400);
            assertThat(scenario.snapshot()).isEqualTo(before); pay(id);
        }
        List<String> actions = List.of("prepare", "pack", "ship", "complete"), states = List.of("PREPARING", "PACKED", "SHIPPING", "COMPLETED");
        for (int i = 0; i < actions.size(); i++) {
            assertUi(id, "delivery", actions.get(i)); httpAction(id, "delivery", actions.get(i), FORGED);
            state(id, states.get(i), method == PaymentMethod.ONLINE || i == 3 ? "PAID" : "UNPAID");
            assertThat(scenario.receipts(id)).hasSize(method == PaymentMethod.ONLINE || i == 3 ? 1 : 0);
        }
        assertUi(id, "delivery", null); receipt(id, method);
        timeline(id, method == PaymentMethod.ONLINE ? List.of("PENDING_PAYMENT", "CONFIRMED", "PREPARING", "PACKED", "SHIPPING", "COMPLETED")
                : List.of("CONFIRMED", "PREPARING", "PACKED", "SHIPPING", "COMPLETED"), method == PaymentMethod.ONLINE ? 2 : 1);
        var before = scenario.snapshot();
        assertThat(scenario.post(scenario.account(STAFF), path(id, "delivery", "complete"), FORGED).statusCode()).isEqualTo(400);
        assertThat(scenario.snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @EnumSource(value = PaymentMethod.class, names = {"PAY_AT_STORE", "ONLINE"})
    void pickupHttpUiVerifiesCustomerCodeAndCompletesPaymentBeforeHistory(PaymentMethod method) throws Exception {
        long id = create(1, FulfillmentType.STORE_PICKUP, method);
        if (method == PaymentMethod.ONLINE) { assertUi(id, "pickup", null); pay(id); }
        assertUi(id, "pickup", "prepare"); httpAction(id, "pickup", "prepare", FORGED); state(id, "PREPARING", method == PaymentMethod.ONLINE ? "PAID" : "UNPAID");
        assertUi(id, "pickup", "ready"); httpAction(id, "pickup", "ready", FORGED);
        var ready = scenario.order(id); String code = scenario.code(id);
        assertThat(code).matches("[A-HJ-NP-Z2-9]{8}"); assertThat(ready.get("ready_at")).isNotNull(); assertThat(ready.get("picked_up_at")).isNull();
        assertUi(id, "pickup", "complete");
        assertThat(scenario.get("/orders/" + id, scenario.account(CUSTOMER)).body()).contains(code);
        if (method == PaymentMethod.PAY_AT_STORE) assertThat(scenario.receipts(id)).isEmpty();
        httpAction(id, "pickup", "complete", FORGED + "&pickupCode=" + Phase9IntegrationScenario.enc("  " + code.toLowerCase(Locale.ROOT) + "  "));
        state(id, "COMPLETED", "PAID"); receipt(id, method); var completed = scenario.order(id);
        assertThat(completed.get("ready_at")).isEqualTo(ready.get("ready_at")); assertThat(completed.get("pickup_code")).isEqualTo(code);
        assertThat((java.sql.Timestamp) completed.get("picked_up_at")).isAfterOrEqualTo((java.sql.Timestamp) ready.get("ready_at"));
        assertThat((java.sql.Timestamp) scenario.receipts(id).get(0).get("paid_at")).isBeforeOrEqualTo((java.sql.Timestamp) completed.get("picked_up_at"));
        timeline(id, method == PaymentMethod.ONLINE ? List.of("PENDING_PAYMENT", "CONFIRMED", "PREPARING", "READY_FOR_PICKUP", "COMPLETED")
                : List.of("CONFIRMED", "PREPARING", "READY_FOR_PICKUP", "COMPLETED"), method == PaymentMethod.ONLINE ? 2 : 1);
        assertUi(id, "pickup", null);
        assertThat(htmlIds(scenario.get("/staff/pickup", scenario.account(STAFF)).body())).doesNotContain(id);
        var before = scenario.snapshot();
        assertThat(scenario.post(scenario.account(STAFF), path(id, "pickup", "complete"), "pickupCode=" + code).statusCode()).isEqualTo(400);
        assertThat(scenario.snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @CsvSource({"DELIVERY,pack", "DELIVERY,ship", "DELIVERY,complete", "STORE_PICKUP,ready", "STORE_PICKUP,complete"})
    void jumpingAheadOfConfirmedIsRejectedRegardlessOfForgedTargetStatus(FulfillmentType type, String action) throws Exception {
        long id = create(1, type, type == FulfillmentType.DELIVERY ? PaymentMethod.COD : PaymentMethod.PAY_AT_STORE);
        var before = scenario.snapshot();
        assertThat(scenario.post(scenario.account(STAFF), path(id, type == FulfillmentType.DELIVERY ? "delivery" : "pickup", action), FORGED + "&pickupCode=TEST2345").statusCode()).isEqualTo(400);
        assertThat(scenario.snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @EnumSource(FulfillmentType.class)
    void actionsOfOtherFulfillmentCannotMutateAnOwnOrder(FulfillmentType type) throws Exception {
        long id = create(1, type, type == FulfillmentType.DELIVERY ? PaymentMethod.COD : PaymentMethod.PAY_AT_STORE);
        var before = scenario.snapshot();
        assertThat(scenario.post(scenario.account(STAFF), path(id, type == FulfillmentType.DELIVERY ? "pickup" : "delivery", "prepare"), FORGED).statusCode()).isEqualTo(400);
        assertThat(scenario.snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"WRONG999", "abc", "ABCDEFGH"})
    void wrongOrMissingPickupCodeCannotCollectPaymentOrComplete(String code) throws Exception {
        long id = create(1, FulfillmentType.STORE_PICKUP, PaymentMethod.PAY_AT_STORE);
        pickup(id, PickupAction.PREPARE, null); pickup(id, PickupAction.READY, null); var before = scenario.snapshot();
        var response = scenario.post(scenario.account(STAFF), path(id, "pickup", "complete"), FORGED
                + (code == null ? "" : "&pickupCode=" + Phase9IntegrationScenario.enc(code)));
        assertThat(response.statusCode()).isEqualTo(400); assertThat(response.body()).doesNotContain(scenario.code(id));
        assertThat(scenario.snapshot()).isEqualTo(before); state(id, "READY_FOR_PICKUP", "UNPAID"); assertThat(scenario.receipts(id)).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings = {"delivery/prepare", "delivery/pack", "delivery/ship", "delivery/complete", "pickup/prepare", "pickup/ready", "pickup/complete"})
    void everyCrossStoreMutationIsDeniedBeforeBusinessRulesOrWrites(String action) throws Exception {
        long id = create(2, action.startsWith("delivery") ? FulfillmentType.DELIVERY : FulfillmentType.STORE_PICKUP,
                action.startsWith("delivery") ? PaymentMethod.COD : PaymentMethod.PAY_AT_STORE);
        var before = scenario.snapshot();
        assertThat(scenario.post(scenario.account(STAFF), "/staff/orders/" + id + "/" + action, "storeId=1&store_id=1&staffEmail=staff.govap%40oneshop.vn&pickupCode=TEST2345").statusCode()).isEqualTo(403);
        assertThat(scenario.snapshot()).isEqualTo(before);
    }
    @Test void payAtStoreInconsistentPriorPaidStateOrDuplicateReceiptCannotCompleteAgain() throws Exception {
        long id = create(1, FulfillmentType.STORE_PICKUP, PaymentMethod.PAY_AT_STORE);
        pickup(id, PickupAction.PREPARE, null); pickup(id, PickupAction.READY, null);
        jdbc.update("update dbo.orders set payment_status='PAID' where order_id=?", id);
        assertUi(id, "pickup", null); var before = scenario.snapshot();
        assertThat(scenario.post(scenario.account(STAFF), path(id, "pickup", "complete"), "pickupCode=" + scenario.code(id)).statusCode()).isEqualTo(400);
        assertThat(scenario.snapshot()).isEqualTo(before);
        jdbc.update("update dbo.orders set payment_status='UNPAID' where order_id=?", id);
        jdbc.update("insert into dbo.payments(order_id,method,amount,status,transaction_code,paid_at) select order_id,'PAY_AT_STORE',total_amount,'SUCCESS','TEST-PRIOR-RECEIPT',SYSDATETIME() from dbo.orders where order_id=?", id);
        assertUi(id, "pickup", null); before = scenario.snapshot();
        assertThat(scenario.post(scenario.account(STAFF), path(id, "pickup", "complete"), "pickupCode=" + scenario.code(id)).statusCode()).isEqualTo(400);
        assertThat(scenario.snapshot()).isEqualTo(before); state(id, "READY_FOR_PICKUP", "UNPAID");
    }
    private static List<Long> htmlIds(String html) {
        var matcher = Pattern.compile("data-order-id=\"(\\d+)\"").matcher(html); List<Long> result = new ArrayList<>();
        while (matcher.find()) result.add(Long.parseLong(matcher.group(1))); return result;
    }
    private long queueFixture(long source, long store, String status, boolean delivery) {
        boolean ready = status.equals("READY_FOR_PICKUP"), completed = status.equals("COMPLETED");
        return jdbc.queryForObject("""
                insert into dbo.orders(checkout_id,user_id,store_id,fulfillment_type,payment_method,payment_status,order_status,
                  receiver_name,receiver_phone,shipping_address,pickup_code,ready_at,picked_up_at,total_amount,created_at,updated_at)
                output inserted.order_id
                select checkout_id,user_id,?,?,?, ?,?,receiver_name,receiver_phone,?,?,?, ?,total_amount,created_at,updated_at
                from dbo.orders where order_id=?
                """, Long.class, store, delivery ? "DELIVERY" : "STORE_PICKUP", "ONLINE", completed ? "PAID" : "UNPAID", status,
                delivery ? "Fixture delivery address" : null, ready || completed ? "TEST2345" : null,
                ready || completed ? java.time.LocalDateTime.now().withNano(0) : null,
                completed ? java.time.LocalDateTime.now().withNano(0) : null, source);
    }
    @Test void pickupQueueUsesAssignedStoreTypeOpenStatesPaginationAndHidesAllCodes() throws Exception {
        long source = create(1, FulfillmentType.STORE_PICKUP, PaymentMethod.PAY_AT_STORE);
        for (String status : List.of("PENDING_PAYMENT", "CONFIRMED", "PREPARING", "READY_FOR_PICKUP", "COMPLETED", "CANCELLED"))
            for (long store : List.of(1L, 2L)) queueFixture(source, store, status, false);
        queueFixture(source, 1, "CONFIRMED", true);
        for (int i = 0; i < 23; i++) queueFixture(source, 1, "READY_FOR_PICKUP", false);
        var ids = jdbc.queryForList("select order_id from dbo.orders where store_id=1 and fulfillment_type='STORE_PICKUP' and order_status in ('CONFIRMED','PREPARING','READY_FOR_PICKUP') order by created_at desc,order_id desc", Long.class);
        var page = as(() -> operations.getPickupQueue(0));
        assertThat(page.getTotalElements()).isEqualTo(ids.size()); assertThat(page.getSize()).isEqualTo(20);
        assertThat(page.getContent()).allSatisfy(o -> {
            assertThat(o.order().storeId()).isEqualTo(1L); assertThat(o.order().fulfillmentType()).isEqualTo(FulfillmentType.STORE_PICKUP);
            assertThat(o.order().orderStatus()).isIn(OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP);
            assertThat(o.order().pickupCode()).isNull(); assertThat(o.customerName()).isNotBlank();
        });
        String cookie = scenario.account(STAFF) + "; ONESHOP_STORE=2";
        var first = scenario.get("/staff/pickup?store_id=2&storeId=2&staffEmail=staff.govap%40oneshop.vn", cookie);
        assertThat(first.statusCode()).isEqualTo(200); assertThat(htmlIds(first.body())).containsExactlyElementsOf(ids.subList(0, 20));
        assertThat(first.body()).contains("/staff/orders/", "/staff/pickup?page=1", "aria-current=\"page\"")
                .doesNotContain("TEST2345", "data-store-id=\"2\"");
        assertThat(htmlIds(scenario.get("/staff/pickup?page=1", cookie).body())).containsExactlyElementsOf(ids.subList(20, ids.size()));
        assertThat(as(() -> operations.getPickupQueue(-1)).getContent()).isEqualTo(page.getContent());
        assertThat(as(() -> operations.getPickupQueue(999)).getContent()).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings = {"delivery/prepare", "pickup/prepare"})
    void newActionsRequireCsrfForCookieBearerAndEncodedPaths(String action) throws Exception {
        long id = create(1, action.startsWith("delivery") ? FulfillmentType.DELIVERY : FulfillmentType.STORE_PICKUP,
                action.startsWith("delivery") ? PaymentMethod.COD : PaymentMethod.PAY_AT_STORE);
        String cookie = scenario.account(STAFF), bearer = "Bearer " + cookie.substring("ONESHOP_TOKEN=".length());
        var before = scenario.snapshot(); String normal = "/staff/orders/" + id + "/" + action;
        String encoded = normal.replace("delivery", "%64elivery").replace("pickup", "%70ickup");
        for (String path : List.of(normal, encoded)) {
            assertThat(scenario.raw(path, FORGED, "Cookie", cookie).statusCode()).isEqualTo(403);
            assertThat(scenario.raw(path, FORGED, "Authorization", bearer).statusCode()).isEqualTo(403);
        }
        assertThat(scenario.snapshot()).isEqualTo(before);
        assertThat(scenario.get(normal, cookie).statusCode()).isEqualTo(405);
        httpAction(id, action.split("/")[0], "prepare", FORGED);
    }
    @Test void absentAssignmentDeniesQueueAndMutationsUsingTheExistingJwt() throws Exception {
        long id = create(1, FulfillmentType.STORE_PICKUP, PaymentMethod.PAY_AT_STORE);
        String cookie = scenario.account(STAFF); long user = scalar("select user_id from dbo.users where email=?", STAFF);
        var row = jdbc.queryForMap("select * from dbo.staff_store_assignments where user_id=? and store_id=1", user);
        try {
            jdbc.update("update dbo.staff_store_assignments set status='INACTIVE' where assignment_id=?", row.get("assignment_id"));
            var before = scenario.snapshot();
            assertThat(scenario.get("/staff/pickup?store_id=1", cookie).statusCode()).isEqualTo(403);
            assertThat(scenario.post(cookie, path(id, "pickup", "prepare"), FORGED).statusCode()).isEqualTo(403);
            assertThatThrownBy(() -> pickup(id, PickupAction.PREPARE, null)).isInstanceOf(AccessDeniedException.class);
            assertThat(scenario.snapshot()).isEqualTo(before);
        } finally { jdbc.update("update dbo.staff_store_assignments set status=? where assignment_id=?", row.get("status"), row.get("assignment_id")); }
    }
    @ParameterizedTest @ValueSource(strings = {"khachhang1@example.com", "admin@oneshop.vn"})
    void otherRolesCannotReachNewQueueOrMutateOrders(String email) throws Exception {
        assertThat(scenario.get("/staff/pickup", scenario.account(email)).statusCode()).isEqualTo(403);
        var before = scenario.snapshot();
        assertThat(scenario.post(scenario.account(email), path(1, "delivery", "complete"), FORGED).statusCode()).isEqualTo(403);
        assertThat(scenario.snapshot()).isEqualTo(before);
    }
    @Test void multipleAssignmentsAllowPeerPickupThenRevocationBlocksTheNextMutationImmediately() throws Exception {
        long id = create(2, FulfillmentType.STORE_PICKUP, PaymentMethod.PAY_AT_STORE);
        long user = scalar("select user_id from dbo.users where email=?", STAFF);
        long max = scalar("select coalesce(max(assignment_id),0) from dbo.staff_store_assignments");
        String cookie = scenario.account(STAFF);
        try {
            jdbc.update("insert into dbo.staff_store_assignments(user_id,store_id,status,assigned_at) values (?,2,'ACTIVE',SYSDATETIME()),(?,4,'INACTIVE',SYSDATETIME())", user, user);
            assertThat(as(() -> operations.getPickupQueue(0)).getContent()).extracting(o -> o.order().orderId()).contains(id);
            httpAction(id, "pickup", "prepare", FORGED);
            assertThat(scenario.history(id).get(1)).containsEntry("changed_by_user_id", user);
            jdbc.update("update dbo.staff_store_assignments set status='INACTIVE' where assignment_id>? and store_id=2", max);
            var before = scenario.snapshot();
            assertThat(scenario.post(cookie, path(id, "pickup", "ready"), "store_id=2&storeId=2").statusCode()).isEqualTo(403);
            assertThat(scenario.get("/staff/orders/" + id, cookie).statusCode()).isEqualTo(404);
            assertThat(as(() -> operations.getPickupQueue(0)).getContent()).extracting(o -> o.order().orderId()).doesNotContain(id);
            assertThat(scenario.snapshot()).isEqualTo(before);
        } finally { jdbc.update("delete from dbo.staff_store_assignments where assignment_id>? and user_id=?", max, user); }
    }
    @Test void missingOrderActionReturns404WithoutMutation() throws Exception {
        var before = scenario.snapshot();
        assertThat(scenario.post(scenario.account(STAFF), path(Long.MAX_VALUE, "pickup", "prepare"), FORGED).statusCode()).isEqualTo(404);
        assertThat(scenario.snapshot()).isEqualTo(before);
    }
    private static final class Abort extends RuntimeException { }
    @ParameterizedTest @ValueSource(strings = {"PICKUP_READY", "PAY_AT_STORE_COMPLETE", "COD_COMPLETE"})
    void wrapperKeepsScopeFulfillmentReceiptHistoryAndTimestampsInOneRollbackTransaction(String stage) {
        boolean isDelivery = stage.equals("COD_COMPLETE");
        long id = create(1, isDelivery ? FulfillmentType.DELIVERY : FulfillmentType.STORE_PICKUP,
                isDelivery ? PaymentMethod.COD : PaymentMethod.PAY_AT_STORE);
        if (isDelivery) { delivery(id, DeliveryAction.PREPARE); delivery(id, DeliveryAction.PACK); delivery(id, DeliveryAction.SHIP); }
        else { pickup(id, PickupAction.PREPARE, null); if (!stage.equals("PICKUP_READY")) pickup(id, PickupAction.READY, null); }
        var before = scenario.snapshot();
        assertThatThrownBy(() -> as(() -> new TransactionTemplate(transactions).execute(tx -> {
            if (isDelivery) operations.performDelivery(id, DeliveryAction.COMPLETE);
            else operations.performPickup(id, stage.equals("PICKUP_READY") ? PickupAction.READY : PickupAction.COMPLETE, scenario.code(id));
            assertThat(scenario.order(id).get("order_status")).isEqualTo(stage.equals("PICKUP_READY") ? "READY_FOR_PICKUP" : "COMPLETED");
            if (!stage.equals("PICKUP_READY")) { state(id, "COMPLETED", "PAID"); assertThat(scenario.receipts(id)).hasSize(1); }
            throw new Abort();
        }))).isExactlyInstanceOf(Abort.class);
        assertThat(scenario.snapshot()).isEqualTo(before);
    }
    @Test void racingPickupCompletionCreatesOnePaymentAndOneCompletionWithNoInventoryWrites() throws Exception {
        long id = create(1, FulfillmentType.STORE_PICKUP, PaymentMethod.PAY_AT_STORE);
        pickup(id, PickupAction.PREPARE, null); pickup(id, PickupAction.READY, null);
        String code = scenario.code(id); var before = scenario.snapshot();
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            Callable<String> work = () -> { assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                try { pickup(id, PickupAction.COMPLETE, code); return "OK"; } catch (BadRequestException ex) { return "REJECTED"; } };
            var a = pool.submit(work); var b = pool.submit(work); start.countDown();
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK", "REJECTED");
            state(id, "COMPLETED", "PAID"); receipt(id, PaymentMethod.PAY_AT_STORE);
            assertThat(scenario.history(id)).hasSize(4);
            assertThat(scenario.snapshot().get("inventory_movements")).isEqualTo(before.get("inventory_movements"));
            assertThat(scenario.snapshot().get("store_products")).isEqualTo(before.get("store_products"));
        } finally { start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }
}
