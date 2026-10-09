package com.oneshop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 10 acceptance: real production HTTP routes, JWT/CSRF, services, repositories and SQL Server.
 * Deliberately unconditional: verification must fail, rather than report DONE, when its database is unavailable.
 * Existing Phase 10 suites retain the detailed fault-injection, pagination and business-rule matrices.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
class Phase10IntegrationWebDatabaseTest {
    private static final String STAFF_A = "staff.thuduc@oneshop.vn";
    private static final String STAFF_B = "staff.govap@oneshop.vn";
    private static final String CUSTOMER = Phase9IntegrationScenario.CUSTOMER;
    private static final String FORGED = "store_id=2&storeId=2&staffEmail=staff.govap%40oneshop.vn"
            + "&staff_id=6&targetStatus=COMPLETED&amount=1&ready_at=2000&picked_up_at=2000";
    @Autowired JdbcTemplate jdbc;
    @LocalServerPort int port;
    private Phase9IntegrationScenario scenario;
    private Phase9IntegrationScenario.Cleanup cleanup;

    @BeforeEach void remember() {
        scenario = new Phase9IntegrationScenario(jdbc, "http://localhost:" + port);
        cleanup = scenario.cleanup();
    }

    @AfterEach void restore() { cleanup.close(); }

    private long scalar(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    private long actor(String email) { return scalar("select user_id from dbo.users where email=?", email); }
    private long stock(long store) {
        return scalar("select sp.store_product_id from dbo.store_products sp join dbo.products p on p.product_id=sp.product_id"
                + " where sp.store_id=? and p.sku=?", store, store == 1 ? "COCOON-SERUM-30" : "INNI-TONER-200");
    }
    private int quantity(long id) { return jdbc.queryForObject("select quantity from dbo.store_products where store_product_id=?", Integer.class, id); }
    private long orderStock(long order) { return scalar("select store_product_id from dbo.order_items where order_id=?", order); }
    private String staff(long store) throws Exception { return scenario.account(Phase9IntegrationScenario.STAFF.get(store)); }
    private static String orderPath(long id) { return "/staff/orders/" + id; }
    private static String adjustPath(long id) { return "/staff/stock/" + id + "/adjust"; }
    private static String historyPath(long id) { return "/staff/inventory-history/" + id; }
    private static List<Long> ids(String html, String attribute) {
        var matcher = Pattern.compile(attribute + "=\"(\\d+)\"").matcher(html);
        List<Long> ids = new ArrayList<>();
        while (matcher.find()) ids.add(Long.parseLong(matcher.group(1)));
        return ids;
    }
    private String page(String path, String cookie) throws Exception {
        var response = scenario.get(path, cookie);
        assertThat(response.statusCode()).as(path).isEqualTo(200);
        return response.body();
    }
    private List<Map<String, Object>> movementsAfter(long max) {
        return jdbc.queryForList("select * from dbo.inventory_movements where movement_id>? order by movement_id", max);
    }
    private long movementMax() { return scalar("select coalesce(max(movement_id),0) from dbo.inventory_movements"); }

    /** Compare rendered production reads with independent SQL while the same orders change state. */
    private void verifyReads(String cookie, List<Long> stores) throws Exception {
        var before = scenario.snapshot();
        String dashboard = page("/staff/dashboard?store_id=5", cookie);
        assertThat(ids(dashboard, "data-dashboard-store-id")).containsExactlyInAnyOrderElementsOf(stores);
        String placeholders = String.join(",", stores.stream().map(s -> "?").toList());
        Object[] args = stores.toArray();
        for (long store : stores) {
            String section = sectionValue(dashboard, store);
            Map<String, Long> counts = Map.of(
                    "pending-orders", scalar("select count(*) from dbo.orders where store_id=? and order_status in ('CONFIRMED','PREPARING','PACKED','SHIPPING')", store),
                    "waiting-pickup", scalar("select count(*) from dbo.orders where store_id=? and fulfillment_type='STORE_PICKUP' and order_status='READY_FOR_PICKUP'", store),
                    "low-stock", scalar("select count(*) from dbo.store_products where store_id=? and status='ACTIVE' and quantity between 1 and 5", store),
                    "out-of-stock", scalar("select count(*) from dbo.store_products where store_id=? and status='ACTIVE' and quantity=0", store));
            counts.forEach((metric, count) -> assertThat(section)
                    .contains("data-metric=\"" + metric + "\" data-value=\"" + count + "\""));
        }
        String orderSql = "select order_id from dbo.orders where store_id in (" + placeholders + ")";
        String sort = " order by created_at desc,order_id desc";
        var orders = jdbc.queryForList(orderSql + sort, Long.class, args);
        assertThat(ids(dashboard, "data-order-id")).containsExactlyElementsOf(orders.stream().limit(5).toList());
        String queue = page("/staff/orders?store_id=5", cookie);
        assertThat(ids(queue, "data-order-id")).containsExactlyElementsOf(orders.stream().limit(20).toList());
        assertThat(queue).contains("id=\"order-count\">" + orders.size() + "</strong>");
        var pickups = jdbc.queryForList(orderSql + " and fulfillment_type='STORE_PICKUP' and order_status in ('CONFIRMED','PREPARING','READY_FOR_PICKUP')" + sort, Long.class, args);
        String pickupQueue = page("/staff/pickup?store_id=5", cookie);
        assertThat(ids(pickupQueue, "data-order-id")).containsExactlyElementsOf(pickups.stream().limit(20).toList());
        assertThat(pickupQueue).contains("id=\"pickup-queue-count\">" + pickups.size() + "</strong>");
        var stocks = jdbc.queryForList("select sp.store_product_id,sp.quantity from dbo.store_products sp join dbo.stores s on s.store_id=sp.store_id"
                + " join dbo.products p on p.product_id=sp.product_id where sp.store_id in (" + placeholders + ") order by s.name,p.name,sp.store_product_id", args);
        String stockPage = page("/staff/stock?store_id=5", cookie);
        assertThat(ids(stockPage, "data-store-product-id")).containsExactlyElementsOf(stocks.stream().limit(20)
                .map(s -> ((Number) s.get("store_product_id")).longValue()).toList());
        for (var stock : stocks.stream().limit(20).toList()) {
            String row = stockPage.substring(stockPage.indexOf("data-store-product-id=\"" + stock.get("store_product_id") + "\""));
            assertThat(row.substring(0, row.indexOf("</tr>"))).contains("data-field=\"exact-quantity\">" + stock.get("quantity") + "</td>");
        }
        assertThat(scenario.snapshot()).as("Staff reads do not write any business row").isEqualTo(before);
    }
    private static String sectionValue(String html, long store) {
        String section = html.substring(html.indexOf("data-dashboard-store-id=\"" + store + "\""));
        return section.substring(0, section.indexOf("</section>"));
    }
    private void verifyAllReads() throws Exception {
        for (long store : List.of(1L, 2L, 4L)) verifyReads(staff(store), List.of(store));
    }
    private void state(long order, String status, String payment) {
        assertThat(scenario.order(order)).containsEntry("order_status", status).containsEntry("payment_status", payment);
    }
    private void action(Phase9IntegrationScenario.Mixed f, long id, long store, String action, String body) throws Exception {
        scenario.isolated(f, id, true, () -> {
            var response = scenario.post(staff(store), orderPath(id) + "/" + action, body);
            assertThat(response.statusCode()).as(action).isEqualTo(302);
            assertThat(response.headers().firstValue("Location").orElseThrow()).endsWith(orderPath(id));
        });
    }
    private void payOnline(long order) throws Exception {
        String cookie = scenario.account(CUSTOMER), path = "/orders/" + order + "/payments";
        assertThat(scenario.post(cookie, path + "/attempts", "").statusCode()).isEqualTo(302);
        long payment = scalar("select payment_id from dbo.payments where order_id=? and status='PENDING'", order);
        assertThat(scenario.post(cookie, path + "/" + payment + "/success", "").statusCode()).isEqualTo(302);
    }
    private void receipt(long order, String method) {
        var receipts = scenario.receipts(order);
        assertThat(receipts).hasSize(1);
        assertThat(receipts.get(0)).containsEntry("method", method).containsEntry("status", "SUCCESS")
                .containsEntry("amount", scenario.order(order).get("total_amount"));
        assertThat(receipts.get(0).get("paid_at")).isNotNull();
        assertThat(receipts.get(0).get("transaction_code").toString()).matches("PAY-[0-9a-f-]{36}");
    }
    private void timeline(long order, long store, List<String> states, int systemEntries) {
        var history = scenario.history(order);
        assertThat(history).hasSize(states.size());
        for (int i = 0; i < states.size(); i++) {
            assertThat(history.get(i)).containsEntry("old_status", i == 0 ? null : states.get(i - 1))
                    .containsEntry("new_status", states.get(i))
                    .containsEntry("changed_by_user_id", i < systemEntries ? null : actor(Phase9IntegrationScenario.STAFF.get(store)));
            assertThat(history.get(i).get("note")).isNotNull();
            assertThat(history.get(i).get("changed_at")).isNotNull();
            if (i > 0) assertThat((Timestamp) history.get(i).get("changed_at"))
                    .isAfterOrEqualTo((Timestamp) history.get(i - 1).get("changed_at"));
        }
    }
    private void adjust(String cookie, long id, int quantity, String note) throws Exception {
        var response = scenario.post(cookie, adjustPath(id), "newQuantity=" + quantity + "&note=" + Phase9IntegrationScenario.enc(note) + "&" + FORGED);
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElseThrow()).endsWith(historyPath(id) + "?success=adjusted");
    }

    @Test void staffOperationsFollowOneMultiStoreCheckoutThroughInventoryDashboardDeliveryAndBothPickups() throws Exception {
        long sp = stock(1);
        adjust(staff(1), sp, 5, "Phase 10.5 physical count before checkout");
        verifyAllReads();
        var f = scenario.mixed();
        assertThat(quantity(sp)).isEqualTo(4);
        verifyAllReads();
        var beforePayment = scenario.snapshot();
        assertThat(scenario.post(staff(2), orderPath(f.b()) + "/pickup/prepare", FORGED).statusCode()).isEqualTo(400);
        assertThat(scenario.snapshot()).isEqualTo(beforePayment);
        scenario.isolated(f, f.b(), true, () -> payOnline(f.b()));
        verifyAllReads();
        List<String> deliveryActions = List.of("prepare", "pack", "ship", "complete");
        List<String> deliveryStates = List.of("PREPARING", "PACKED", "SHIPPING", "COMPLETED");
        for (int i = 0; i < deliveryActions.size(); i++) {
            action(f, f.a(), 1, "delivery/" + deliveryActions.get(i), FORGED);
            state(f.a(), deliveryStates.get(i), i == 3 ? "PAID" : "UNPAID");
            assertThat(scenario.receipts(f.a())).hasSize(i == 3 ? 1 : 0);
            verifyReads(staff(1), List.of(1L));
        }
        for (long id : List.of(f.b(), f.c())) {
            long store = id == f.b() ? 2 : 4;
            String payment = id == f.b() ? "PAID" : "UNPAID";
            action(f, id, store, "pickup/prepare", FORGED); state(id, "PREPARING", payment);
            verifyReads(staff(store), List.of(store));
            action(f, id, store, "pickup/ready", FORGED); state(id, "READY_FOR_PICKUP", payment);
            var ready = scenario.order(id); String code = scenario.code(id);
            assertThat(code).matches("[A-HJ-NP-Z2-9]{8}");
            assertThat(ready.get("ready_at")).isNotNull(); assertThat(ready.get("picked_up_at")).isNull();
            assertThat(page(orderPath(id), staff(store))).doesNotContain(code);
            assertThat(page("/orders/" + id, scenario.account(CUSTOMER))).contains(code);
            verifyReads(staff(store), List.of(store));
            var beforeWrongCode = scenario.snapshot();
            assertThat(scenario.post(staff(store), orderPath(id) + "/pickup/complete", "pickupCode=INVALID!").statusCode()).isEqualTo(400);
            assertThat(scenario.snapshot()).isEqualTo(beforeWrongCode);
            action(f, id, store, "pickup/complete", "pickupCode=" + Phase9IntegrationScenario.enc("  " + code.toLowerCase(Locale.ROOT) + "  ") + "&" + FORGED);
            state(id, "COMPLETED", "PAID");
            var completed = scenario.order(id);
            assertThat(completed.get("ready_at")).isEqualTo(ready.get("ready_at"));
            assertThat(completed.get("pickup_code")).isEqualTo(code);
            assertThat((Timestamp) completed.get("picked_up_at")).isAfterOrEqualTo((Timestamp) ready.get("ready_at"));
            assertThat((Timestamp) scenario.receipts(id).get(0).get("paid_at")).isBeforeOrEqualTo((Timestamp) completed.get("picked_up_at"));
            assertThat((Timestamp) scenario.history(id).getLast().get("changed_at")).isAfterOrEqualTo((Timestamp) completed.get("picked_up_at"));
            verifyReads(staff(store), List.of(store));
        }
        receipt(f.a(), "COD"); receipt(f.b(), "ONLINE"); receipt(f.c(), "PAY_AT_STORE");
        timeline(f.a(), 1, List.of("CONFIRMED", "PREPARING", "PACKED", "SHIPPING", "COMPLETED"), 1);
        timeline(f.b(), 2, List.of("PENDING_PAYMENT", "CONFIRMED", "PREPARING", "READY_FOR_PICKUP", "COMPLETED"), 2);
        timeline(f.c(), 4, List.of("CONFIRMED", "PREPARING", "READY_FOR_PICKUP", "COMPLETED"), 1);
        assertThat(quantity(sp)).isEqualTo(4);
        assertThat(page(historyPath(sp), staff(1))).contains("STOCK_ADJUST", "ORDER", "Phase 10.5 physical count before checkout");
        verifyAllReads();
    }

    @ParameterizedTest @ValueSource(strings = {"query", "form-body", "json-body", "cookie"})
    void tc11PeerOrderAndInventoryCannotBeReadOrMutatedByForgingAnyStoreSource(String source) throws Exception {
        var f = scenario.mixed(); payOnline(f.b());
        action(f, f.b(), 2, "pickup/prepare", ""); action(f, f.b(), 2, "pickup/ready", "");
        String cookie = staff(1) + (source.equals("cookie") ? "; ONESHOP_STORE=2; store_id=2" : "");
        var before = scenario.snapshot();
        for (String path : List.of(orderPath(f.b()), adjustPath(orderStock(f.b())), historyPath(orderStock(f.b())))) {
            var response = forgedGet(path, cookie, source);
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.body()).doesNotContain(scenario.code(f.b()), "data-movement-id", "id=\"current-quantity\"", "id=\"fulfillment-action\"");
        }
        var queue = forgedGet("/staff/orders", cookie, source);
        assertThat(queue.statusCode()).isEqualTo(200);
        assertThat(ids(queue.body(), "data-order-id")).contains(f.a()).doesNotContain(f.b(), f.c());
        for (String action : List.of("delivery/prepare", "delivery/pack", "delivery/ship", "delivery/complete", "pickup/prepare", "pickup/ready", "pickup/complete")) {
            assertThat(scenario.post(cookie, orderPath(f.b()) + "/" + action + "?store_id=1", FORGED + "&pickupCode=" + scenario.code(f.b())).statusCode()).isEqualTo(403);
        }
        assertThat(scenario.post(cookie, adjustPath(orderStock(f.b())) + "?store_id=1", "newQuantity=1&note=Forged+peer+count&" + FORGED).statusCode()).isEqualTo(403);
        assertThat(scenario.snapshot()).as("TC-11: every peer aggregate and stock row stays identical").isEqualTo(before);
    }
    private HttpResponse<String> forgedGet(String path, String cookie, String source) throws Exception {
        if (source.equals("query")) return scenario.get(path + "?" + FORGED, cookie);
        if (source.equals("cookie")) return scenario.get(path, cookie);
        String body = source.equals("json-body") ? "{\"store_id\":2,\"storeId\":2,\"staffEmail\":\"" + STAFF_B + "\"}" : FORGED;
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Cookie", cookie).header("Content-Type", source.equals("json-body") ? "application/json" : "application/x-www-form-urlencoded")
                .method("GET", HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void tc13PhysicalCountCreatesOneCompleteAuditAndImmediatelyUpdatesDashboardAndExactStock() throws Exception {
        long sp = stock(1), max = movementMax(); int original = quantity(sp);
        int actual = original == 3 ? 4 : 3;
        // SQL Server DATETIME2(0) rounds to seconds; compare the request window at that schema precision.
        var before = scenario.snapshot();
        Timestamp started = Timestamp.valueOf(java.time.LocalDateTime.now().withNano(0));
        adjust(staff(1), sp, actual, "  Phase 10.5 TC-13 count  ");
        Timestamp finished = Timestamp.valueOf(java.time.LocalDateTime.now().plusSeconds(1).withNano(0));
        assertThat(quantity(sp)).isEqualTo(actual);
        var movements = movementsAfter(max); assertThat(movements).hasSize(1);
        assertThat(movements.get(0)).containsEntry("store_product_id", sp).containsEntry("type", "STOCK_ADJUST")
                .containsEntry("quantity_before", original).containsEntry("quantity_after", actual)
                .containsEntry("quantity_change", actual - original).containsEntry("staff_id", actor(STAFF_A))
                .containsEntry("note", "Phase 10.5 TC-13 count").containsEntry("reference_order_id", null);
        // DATETIME2(0) can round up exactly to finished; that endpoint belongs to the allowed precision window.
        assertThat((Timestamp) movements.get(0).get("created_at")).isAfterOrEqualTo(started).isBeforeOrEqualTo(finished);
        var after = scenario.snapshot();
        for (String table : Phase9IntegrationScenario.TABLES) if (!Set.of("store_products", "inventory_movements").contains(table))
            assertThat(after.get(table)).as(table).isEqualTo(before.get(table));
        assertThat(after.get("store_products").stream().filter(s -> !s.get("store_product_id").equals(sp)).toList())
                .isEqualTo(before.get("store_products").stream().filter(s -> !s.get("store_product_id").equals(sp)).toList());
        var prior = new HashMap<>(before.get("store_products").stream().filter(s -> s.get("store_product_id").equals(sp)).findFirst().orElseThrow());
        var current = new HashMap<>(after.get("store_products").stream().filter(s -> s.get("store_product_id").equals(sp)).findFirst().orElseThrow());
        for (String field : List.of("quantity", "updated_at")) { prior.remove(field); current.remove(field); }
        assertThat(current).isEqualTo(prior);
        assertThat(after.get("inventory_movements").stream().filter(m -> ((Number) m.get("movement_id")).longValue() <= max).toList())
                .isEqualTo(before.get("inventory_movements"));
        assertThat(ids(page(historyPath(sp), staff(1)), "data-movement-id")).contains(((Number) movements.get(0).get("movement_id")).longValue());
        verifyReads(staff(1), List.of(1L)); verifyReads(staff(2), List.of(2L));
    }

    @ParameterizedTest @ValueSource(strings = {"ABSENT", "INACTIVE"})
    void absentOrInactiveAssignmentBlocksEveryStaffModuleAndMutationWithForgedStore(String mode) throws Exception {
        String email = STAFF_A; Long fixtureUser = null;
        var assignments = jdbc.queryForList("select assignment_id,status from dbo.staff_store_assignments where user_id=?", actor(STAFF_A));
        try {
            String cookie;
            if (mode.equals("ABSENT")) {
                email = "phase105-" + UUID.randomUUID() + "@example.com";
                fixtureUser = jdbc.queryForObject("insert into dbo.users(role_id,email,password_hash,full_name,status) output inserted.user_id"
                        + " select role_id,?,password_hash,'Phase 10.5 unassigned staff','ACTIVE' from dbo.users where email=?", Long.class, email, STAFF_A);
                cookie = scenario.account(email);
            } else {
                cookie = scenario.account(email); // Reuse a JWT issued while the assignment was ACTIVE.
                jdbc.update("update dbo.staff_store_assignments set status='INACTIVE' where user_id=?", actor(email));
            }
            cookie += "; ONESHOP_STORE=1";
            var before = scenario.snapshot();
            for (String path : List.of("/staff/dashboard", "/staff/orders", "/staff/pickup", "/staff/stock", "/staff/inventory-history",
                    orderPath(1), adjustPath(stock(1)), historyPath(stock(1)))) {
                var response = scenario.get(path + "?store_id=1&staffEmail=" + STAFF_A, cookie);
                assertThat(response.statusCode()).as(mode + " " + path).isEqualTo(403);
                assertThat(response.body()).doesNotContain("data-order-id", "data-movement-id", "data-store-product-id", "data-dashboard-store-id");
            }
            for (String action : List.of("delivery/prepare", "delivery/pack", "delivery/ship", "delivery/complete", "pickup/prepare", "pickup/ready", "pickup/complete"))
                assertThat(scenario.post(cookie, orderPath(1) + "/" + action, FORGED).statusCode()).isEqualTo(403);
            assertThat(scenario.post(cookie, adjustPath(stock(1)), "newQuantity=1&note=Denied&" + FORGED).statusCode()).isEqualTo(403);
            assertThat(page("/staff", cookie)).contains("id=\"no-store-scope\"").doesNotContain("data-order-id", "data-store-product-id");
            assertThat(scenario.snapshot()).isEqualTo(before);
        } finally {
            if (fixtureUser != null) jdbc.update("delete from dbo.users where user_id=?", fixtureUser);
            for (var row : assignments) jdbc.update("update dbo.staff_store_assignments set status=? where assignment_id=?", row.get("status"), row.get("assignment_id"));
        }
    }

    @Test void revokingOneOfMultipleAssignmentsImmediatelyRemovesPeerReadsAndWritesAcrossAllModules() throws Exception {
        var f = scenario.mixed(); String cookie = staff(1); long user = actor(STAFF_A);
        long assignment = jdbc.queryForObject("insert into dbo.staff_store_assignments(user_id,store_id,status,assigned_at) output inserted.assignment_id"
                + " values(?,2,'ACTIVE',SYSDATETIME())", Long.class, user);
        try {
            verifyReads(cookie, List.of(1L, 2L));
            assertThat(page(orderPath(f.b()), cookie)).contains("id=\"order-customer\"");
            adjust(cookie, orderStock(f.b()), quantity(orderStock(f.b())) + 1, "Authorized second Store");
            payOnline(f.b()); action(f, f.b(), 1, "pickup/prepare", "");
            assertThat(scenario.history(f.b()).getLast()).containsEntry("changed_by_user_id", user);
            jdbc.update("update dbo.staff_store_assignments set status='INACTIVE' where assignment_id=?", assignment);
            var before = scenario.snapshot(); verifyReads(cookie, List.of(1L));
            for (String path : List.of(orderPath(f.b()), adjustPath(orderStock(f.b())), historyPath(orderStock(f.b()))))
                assertThat(scenario.get(path + "?store_id=2", cookie + "; ONESHOP_STORE=2").statusCode()).isEqualTo(404);
            assertThat(scenario.post(cookie, orderPath(f.b()) + "/pickup/ready", FORGED).statusCode()).isEqualTo(403);
            assertThat(scenario.post(cookie, adjustPath(orderStock(f.b())), "newQuantity=1&note=Revoked&" + FORGED).statusCode()).isEqualTo(403);
            assertThat(scenario.snapshot()).isEqualTo(before);
        } finally { jdbc.update("delete from dbo.staff_store_assignments where assignment_id=?", assignment); }
    }

    @Test void failedOnlineSiblingUpdatesOnlyItsStoreDashboardAndInventoryWhileOtherStaffOrdersContinue() throws Exception {
        var f = scenario.mixed(); long sp = orderStock(f.b()); int reserved = quantity(sp); long max = movementMax();
        verifyAllReads(); scenario.onlineResult(f, false);
        state(f.b(), "CANCELLED", "FAILED"); assertThat(quantity(sp)).isEqualTo(reserved + 1);
        assertThat(movementsAfter(max)).singleElement().satisfies(m -> assertThat(m).containsEntry("type", "CANCEL_ORDER")
                .containsEntry("store_product_id", sp).containsEntry("reference_order_id", f.b())
                .containsEntry("quantity_before", reserved).containsEntry("quantity_after", reserved + 1).containsEntry("quantity_change", 1));
        verifyAllReads();
        assertThat(page(historyPath(sp), staff(2))).contains("CANCEL_ORDER", "ORDER");
        var before = scenario.snapshot();
        assertThat(scenario.post(staff(2), orderPath(f.b()) + "/pickup/prepare", "").statusCode()).isEqualTo(400);
        assertThat(scenario.snapshot()).isEqualTo(before);
        action(f, f.a(), 1, "delivery/prepare", ""); action(f, f.c(), 4, "pickup/prepare", "");
        state(f.a(), "PREPARING", "UNPAID"); state(f.c(), "PREPARING", "UNPAID");
        state(f.b(), "CANCELLED", "FAILED"); verifyAllReads();
    }

    @Test void racingHttpAdjustmentAndCheckoutMaintainOneSerialStockLedgerAndCorrectSnapshot() throws Exception {
        long sp = stock(1); String cookie = staff(1), customer = scenario.account(CUSTOMER);
        assertThat(scenario.post(customer, "/cart/items", "storeProductId=" + sp + "&quantity=1").statusCode()).isEqualTo(302);
        long line = scalar("select ci.cart_item_id from dbo.cart_items ci join dbo.carts c on c.cart_id=ci.cart_id join dbo.users u on u.user_id=c.user_id"
                + " where u.email=? and ci.store_product_id=?", CUSTOMER, sp);
        assertThat(scenario.post(customer, "/cart/items/" + line, "quantity=1").statusCode()).isEqualTo(302);
        int original = quantity(sp), physical = original + 7; long max = movementMax();
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var count = pool.submit(() -> { assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                adjust(cookie, sp, physical, "Concurrent checkout count"); return true; });
            var checkout = pool.submit(() -> { assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return scenario.post(customer, "/checkout", "cartItemIds=" + line
                        + "&groups[0].storeId=1&groups[0].fulfillmentType=DELIVERY&groups[0].paymentMethod=COD"
                        + "&groups[0].receiverName=Phase105+race&groups[0].receiverPhone=0912345678&groups[0].shippingAddress=10+Race+Street"); });
            start.countDown(); assertThat(count.get(30, TimeUnit.SECONDS)).isTrue();
            var response = checkout.get(30, TimeUnit.SECONDS); assertThat(response.statusCode()).isEqualTo(302);
            var movements = movementsAfter(max); assertThat(movements).hasSize(2);
            assertThat(movements).extracting(m -> m.get("type")).containsExactlyInAnyOrder("ORDER", "STOCK_ADJUST");
            assertThat(movements.get(0)).containsEntry("quantity_before", original);
            assertThat(movements.get(1).get("quantity_before")).isEqualTo(movements.get(0).get("quantity_after"));
            assertThat(quantity(sp)).isEqualTo(movements.get(1).get("quantity_after")).isIn(physical, physical - 1);
            for (var m : movements) {
                assertThat(m).containsEntry("store_product_id", sp);
                assertThat((Integer) m.get("quantity_change")).isEqualTo((Integer) m.get("quantity_after") - (Integer) m.get("quantity_before"));
                if (m.get("type").equals("STOCK_ADJUST")) assertThat(m).containsEntry("quantity_after", physical)
                        .containsEntry("staff_id", actor(STAFF_A)).containsEntry("note", "Concurrent checkout count");
                else {
                    assertThat(m).containsEntry("quantity_change", -1).containsEntry("staff_id", null);
                    long order = ((Number) m.get("reference_order_id")).longValue(); state(order, "CONFIRMED", "UNPAID");
                    assertThat(orderStock(order)).isEqualTo(sp);
                    assertThat(scalar("select quantity from dbo.order_items where order_id=?", order)).isEqualTo(1);
                }
            }
            verifyReads(cookie, List.of(1L));
        } finally { start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test void racingHttpPickupCompletionCollectsPayAtStoreOnceAndRejectsTheDuplicateWithoutStockChanges() throws Exception {
        var f = scenario.mixed(); action(f, f.c(), 4, "pickup/prepare", ""); action(f, f.c(), 4, "pickup/ready", "");
        String cookie = staff(4), body = "pickupCode=" + scenario.code(f.c()); var before = scenario.snapshot();
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> { assertThat(start.await(10, TimeUnit.SECONDS)).isTrue(); return scenario.post(cookie, orderPath(f.c()) + "/pickup/complete", body).statusCode(); });
            var b = pool.submit(() -> { assertThat(start.await(10, TimeUnit.SECONDS)).isTrue(); return scenario.post(cookie, orderPath(f.c()) + "/pickup/complete", body).statusCode(); });
            start.countDown();
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(302, 400);
            state(f.c(), "COMPLETED", "PAID"); receipt(f.c(), "PAY_AT_STORE");
            timeline(f.c(), 4, List.of("CONFIRMED", "PREPARING", "READY_FOR_PICKUP", "COMPLETED"), 1);
            var after = scenario.snapshot();
            for (String table : List.of("store_products", "inventory_movements", "order_items", "checkout_sessions", "cart_items", "carts"))
                assertThat(after.get(table)).as(table).isEqualTo(before.get(table));
            for (String table : List.of("orders", "payments", "order_status_history")) {
                assertThat(after.get(table).stream().filter(r -> !r.get("order_id").equals(f.c())).toList())
                        .isEqualTo(before.get(table).stream().filter(r -> !r.get("order_id").equals(f.c())).toList());
            }
            verifyReads(cookie, List.of(4L));
        } finally { start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }
}
