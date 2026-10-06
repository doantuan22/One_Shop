package com.oneshop;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Shared real HTTP/JDBC scenarios for Tomcat tests and the independently launched dev JAR. */
final class Phase9IntegrationScenario {
    static final String CUSTOMER = "khachhang1@example.com";
    static final String OTHER_CUSTOMER = "khachhang2@example.com";
    static final Map<Long, String> STAFF = Map.of(1L, "staff.thuduc@oneshop.vn", 2L, "staff.govap@oneshop.vn", 4L, "staff.quan10@oneshop.vn");
    static final List<String> TABLES = List.of("checkout_sessions", "orders", "order_items", "payments",
            "order_status_history", "inventory_movements", "store_products", "cart_items", "carts");
    private static final String FORGED = "userId=6&storeId=5&amount=1&method=COD&paymentMethod=COD&status=FAILED"
            + "&targetStatus=CANCELLED&transaction_code=HACK&transactionCode=HACK&ready_at=2000&readyAt=2000"
            + "&picked_up_at=2000&pickedUpAt=2000&pickupCode=HACK";
    final JdbcTemplate jdbc;
    private final String base;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final Map<String, String> accounts = new HashMap<>();

    Phase9IntegrationScenario(JdbcTemplate jdbc, String base) { this.jdbc = jdbc; this.base = base; }
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
    static long number(Object value) { return ((Number) value).longValue(); }
    static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        TABLES.forEach(t -> result.put(t, rows(t))); return result;
    }
    List<Map<String, Object>> rows(String table) { return jdbc.queryForList("select * from dbo." + table + " order by 1"); }
    Map<String, List<Map<String, Object>>> scope() {
        return Map.of("users", jdbc.queryForList("select user_id,role_id,email,full_name,phone,status,created_at,updated_at from dbo.users order by 1"),
                "stores", rows("stores"), "assignments", rows("staff_store_assignments"));
    }
    final class Cleanup implements AutoCloseable {
        private final SeedGuard guard = new SeedGuard(jdbc);
        private final Map<String, List<Map<String, Object>>> before = snapshot(), scopeBefore = scope();
        private final List<Map<String, Object>> catalog = rows("products");
        Cleanup() { guard.remember(); }
        public void close() {
            guard.restore();
            check(snapshot().equals(before), "exact cleanup of nine tables, ids and timestamps");
            check(scope().equals(scopeBefore), "exact scope cleanup");
            check(rows("products").equals(catalog), "exact catalog cleanup");
        }
    }
    Cleanup cleanup() { return new Cleanup(); }
    record Mixed(long checkout, long a, long b, long c) { List<Long> ids() { return List.of(a, b, c); } }
    Map<String, Object> order(long id) { return jdbc.queryForMap("select * from dbo.orders where order_id=?", id); }
    String code(long id) { return (String) order(id).get("pickup_code"); }
    List<Map<String, Object>> receipts(long id) { return jdbc.queryForList("select * from dbo.payments where order_id=? order by payment_id", id); }
    List<Map<String, Object>> history(long id) { return jdbc.queryForList("select * from dbo.order_status_history where order_id=? order by changed_at,history_id", id); }
    Map<String, Object> aggregate(long id) {
        return Map.of("order", order(id), "items", jdbc.queryForList("select * from dbo.order_items where order_id=? order by 1", id),
                "payments", receipts(id), "history", history(id),
                "movements", jdbc.queryForList("select * from dbo.inventory_movements where reference_order_id=? order by 1", id));
    }
    private void state(long id, String status, String payment) {
        check(order(id).get("order_status").equals(status) && order(id).get("payment_status").equals(payment), "order state/payment");
    }
    String account(String email) throws Exception {
        if (!accounts.containsKey(email)) {
            var response = http.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/login")).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\"OneShop@123\"}")).build(), HttpResponse.BodyHandlers.ofString());
            check(response.statusCode() == 200, "seed login");
            Matcher token = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(response.body());
            check(token.find(), "JWT received"); accounts.put(email, "ONESHOP_TOKEN=" + token.group(1));
        }
        return accounts.get(email);
    }
    HttpResponse<String> get(String path, String cookie) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).header("Cookie", cookie).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    HttpResponse<String> raw(String path, String body, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (headers.length > 0) request.headers(headers);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    HttpResponse<String> post(String cookie, String path, String body) throws Exception {
        var page = get("/login", "");
        Matcher token = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body()); check(token.find(), "CSRF form");
        String csrfCookie = page.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN="))
                .map(c -> c.split(";")[0]).findFirst().orElseThrow();
        return raw(path, body + "&_csrf=" + enc(token.group(1)), "Cookie", cookie + "; " + csrfCookie);
    }
    private static String group(int index, long store, String type, String method) {
        String p = "groups[" + index + "].";
        return p + "storeId=" + store + "&" + p + "fulfillmentType=" + type + "&" + p + "paymentMethod=" + method
                + "&" + p + "receiverName=Phase9+integration+receiver&" + p + "receiverPhone=0912345678"
                + (type.equals("DELIVERY") ? "&" + p + "shippingAddress=9+Integration+Street" : "");
    }
    private long add(long store, String sku) throws Exception {
        long sp = jdbc.queryForObject("select sp.store_product_id from dbo.store_products sp join dbo.products p on p.product_id=sp.product_id where sp.store_id=? and p.sku=?", Long.class, store, sku);
        check(post(account(CUSTOMER), "/cart/items", "storeProductId=" + sp + "&quantity=1").statusCode() == 302, "real cart add");
        long line = jdbc.queryForObject("select ci.cart_item_id from dbo.cart_items ci join dbo.carts c on c.cart_id=ci.cart_id join dbo.users u on u.user_id=c.user_id where u.email=? and ci.store_product_id=?", Long.class, CUSTOMER, sp);
        check(post(account(CUSTOMER), "/cart/items/" + line, "quantity=1").statusCode() == 302, "fixture one unit, including preexisting cart lines");
        return line;
    }
    private long place(List<Long> lines, List<String> choices) throws Exception {
        var response = post(account(CUSTOMER), "/checkout", String.join("&", lines.stream().map(id -> "cartItemIds=" + id).toList()) + "&" + String.join("&", choices));
        check(response.statusCode() == 302, "single real checkout");
        Matcher id = Pattern.compile("/checkout/(\\d+)").matcher(response.headers().firstValue("Location").orElseThrow());
        check(id.find(), "checkout result redirect"); return Long.parseLong(id.group(1));
    }
    Mixed mixed() throws Exception {
        var stock = rows("store_products"); int sessions = rows("checkout_sessions").size(), orders = rows("orders").size();
        long checkout = place(List.of(add(1, "COCOON-SERUM-30"), add(2, "INNI-TONER-200"), add(4, "INNI-CLEANS-120")),
                List.of(group(0, 1, "DELIVERY", "COD"), group(1, 2, "STORE_PICKUP", "ONLINE"), group(2, 4, "STORE_PICKUP", "PAY_AT_STORE")));
        var created = jdbc.queryForList("select * from dbo.orders where checkout_id=? order by store_id", checkout);
        check(created.size() == 3 && rows("orders").size() == orders + 3 && rows("checkout_sessions").size() == sessions + 1, "one session, three orders");
        var f = new Mixed(checkout, number(created.get(0).get("order_id")), number(created.get(1).get("order_id")), number(created.get(2).get("order_id")));
        state(f.a, "CONFIRMED", "UNPAID"); state(f.b, "PENDING_PAYMENT", "UNPAID"); state(f.c, "CONFIRMED", "UNPAID");
        BigDecimal sum = BigDecimal.ZERO;
        for (long id : f.ids()) {
            var o = order(id); check(receipts(id).isEmpty(), "no payment at checkout");
            var items = jdbc.queryForList("select * from dbo.order_items where order_id=?", id); check(items.size() == 1, "single snapshot per store");
            var item = items.get(0); long sp = number(item.get("store_product_id"));
            var prior = stock.stream().filter(s -> number(s.get("store_product_id")) == sp).findFirst().orElseThrow();
            var current = jdbc.queryForMap("select * from dbo.store_products where store_product_id=?", sp);
            check(number(item.get("quantity")) == 1 && number(current.get("quantity")) == number(prior.get("quantity")) - 1, "Q -> Q-1 exactly once");
            check(current.get("store_id").equals(o.get("store_id")) && item.get("unit_price").equals(prior.get("price")) && item.get("subtotal").equals(o.get("total_amount")), "store ownership and money snapshot");
            var movement = jdbc.queryForMap("select * from dbo.inventory_movements where reference_order_id=?", id);
            check(movement.get("type").equals("ORDER") && number(movement.get("quantity_change")) == -1 && movement.get("store_product_id").equals(sp)
                    && movement.get("quantity_before").equals(prior.get("quantity")) && movement.get("quantity_after").equals(current.get("quantity")) && movement.get("staff_id") == null, "ORDER movement FK/before/after");
            sum = sum.add((BigDecimal) o.get("total_amount"));
        }
        check(sum.equals(jdbc.queryForObject("select total_amount from dbo.checkout_sessions where checkout_id=?", BigDecimal.class, checkout)), "grouping total snapshot");
        check(jdbc.queryForObject("select count(*) from INFORMATION_SCHEMA.COLUMNS where TABLE_NAME='payments' and COLUMN_NAME in ('checkout_id','checkout_session_id')", Integer.class) == 0, "Payment has no checkout FK");
        var result = get("/checkout/" + checkout, account(CUSTOMER)); check(result.statusCode() == 200, "checkout UI");
        f.ids().forEach(id -> check(result.body().contains("/orders/" + id), "result to own order detail"));
        return f;
    }
    @FunctionalInterface interface Action { void run() throws Exception; }
    void isolated(Mixed f, long target, boolean inventoryUnchanged, Action action) throws Exception {
        var siblings = new LinkedHashMap<Long, Map<String, Object>>();
        f.ids().stream().filter(id -> id != target).forEach(id -> siblings.put(id, aggregate(id)));
        var grouping = jdbc.queryForMap("select * from dbo.checkout_sessions where checkout_id=?", f.checkout);
        var stock = rows("store_products"); var movements = rows("inventory_movements");
        action.run();
        siblings.forEach((id, before) -> check(aggregate(id).equals(before), "sibling order/items/payments/history/movements exact unchanged"));
        check(jdbc.queryForMap("select * from dbo.checkout_sessions where checkout_id=?", f.checkout).equals(grouping), "grouping exact unchanged, no aggregation");
        if (inventoryUnchanged) check(rows("store_products").equals(stock) && rows("inventory_movements").equals(movements), "fulfillment/payment success do not touch inventory");
    }
    private void mutate(Mixed f, long id, String path, String email, String body) throws Exception {
        isolated(f, id, true, () -> check(post(account(email), path, body).statusCode() == 302, "authorized fixed action"));
    }
    void delivery(Mixed f) throws Exception {
        String[] status = {"PREPARING", "PACKED", "SHIPPING", "COMPLETED"}; int i = 0;
        for (String action : List.of("prepare", "pack", "ship", "complete")) {
            String path = "/staff/orders/delivery/" + f.a + "/" + action;
            check(get("/staff/orders/delivery/" + f.a, account(STAFF.get(1L))).body().contains(path), "delivery next UI action");
            mutate(f, f.a, path, STAFF.get(1L), FORGED); state(f.a, status[i], i == 3 ? "PAID" : "UNPAID");
            check(receipts(f.a).size() == (i == 3 ? 1 : 0), "COD payment completion only"); i++;
        }
        receipt(f.a, "COD", "SUCCESS"); timeline(f.a, STAFF.get(1L), List.of("CONFIRMED", "PREPARING", "PACKED", "SHIPPING", "COMPLETED"), 1);
    }
    long onlineResult(Mixed f, boolean success) throws Exception {
        check(get("/orders/" + f.b, account(CUSTOMER)).body().contains("/orders/" + f.b + "/payments"), "pending customer payment link");
        mutate(f, f.b, "/orders/" + f.b + "/payments/attempts", CUSTOMER, FORGED);
        long p = number(receipts(f.b).get(0).get("payment_id"));
        isolated(f, f.b, success, () -> check(post(account(CUSTOMER), "/orders/" + f.b + "/payments/" + p + (success ? "/success" : "/failure"), FORGED).statusCode() == 302, "online result"));
        state(f.b, success ? "CONFIRMED" : "CANCELLED", success ? "PAID" : "FAILED");
        receipt(f.b, "ONLINE", success ? "SUCCESS" : "FAILED"); return p;
    }
    void pickupReady(Mixed f, long id, long store) throws Exception {
        check(!get("/orders/" + id, account(CUSTOMER)).body().contains("id=\"pickup-code\""), "code hidden before ready");
        for (String action : List.of("prepare", "ready")) {
            String path = "/staff/orders/pickup/" + id + "/" + action;
            check(get("/staff/orders/pickup/" + id, account(STAFF.get(store))).body().contains(path), "pickup next UI action");
            mutate(f, id, path, STAFF.get(store), FORGED);
        }
        state(id, "READY_FOR_PICKUP", id == f.b ? "PAID" : "UNPAID");
        var o = order(id); check(code(id).matches("[A-HJ-NP-Z2-9]{8}") && o.get("ready_at") != null && o.get("picked_up_at") == null, "server ready fields");
        check(get("/orders/" + id, account(CUSTOMER)).body().contains(code(id)), "only owner can see READY code");
        check(!get("/staff/orders/pickup/" + id, account(STAFF.get(store))).body().contains(code(id)), "stored code absent from staff DTO/UI");
        if (id == f.c) check(receipts(id).isEmpty(), "PAY_AT_STORE still no payment at READY");
    }
    void pickupComplete(Mixed f, long id, long store) throws Exception {
        var ready = order(id); var before = snapshot();
        var wrong = post(account(STAFF.get(store)), "/staff/orders/pickup/" + id + "/complete", "pickupCode=WRONG999");
        check(wrong.statusCode() == 400 && !wrong.body().contains(code(id)) && snapshot().equals(before), "wrong code no leak/zero mutation across checkout");
        String input = "pickupCode=" + enc("  " + code(id).toLowerCase(Locale.ROOT) + "  ") + "&" + FORGED.replace("&pickupCode=HACK", "");
        mutate(f, id, "/staff/orders/pickup/" + id + "/complete", STAFF.get(store), input); state(id, "COMPLETED", "PAID");
        var completed = order(id); check(completed.get("picked_up_at") != null && completed.get("ready_at").equals(ready.get("ready_at")) && completed.get("pickup_code").equals(ready.get("pickup_code")), "completion time, stable code/ready time");
        var page = get("/orders/" + id, account(CUSTOMER)); check(page.body().contains(code(id)) && page.body().contains("Mã này không còn sử dụng được."), "completed code historical only");
        receipt(id, id == f.b ? "ONLINE" : "PAY_AT_STORE", "SUCCESS");
        timeline(id, STAFF.get(store), id == f.b ? List.of("PENDING_PAYMENT", "CONFIRMED", "PREPARING", "READY_FOR_PICKUP", "COMPLETED")
                : List.of("CONFIRMED", "PREPARING", "READY_FOR_PICKUP", "COMPLETED"), id == f.b ? 2 : 1);
    }
    private void receipt(long id, String method, String status) {
        var payments = receipts(id); check(payments.size() == 1, "one receipt belongs to this order"); var p = payments.get(0);
        check(p.get("order_id").equals(id) && p.get("method").equals(method) && p.get("status").equals(status) && p.get("amount").equals(order(id).get("total_amount")), "receipt FK/method/status/purchase amount");
        check(p.get("transaction_code").toString().matches("PAY-[0-9a-f-]{36}") && (p.get("paid_at") != null) == status.equals("SUCCESS") && p.get("created_at") != null, "server receipt code/time");
    }
    private void timeline(long id, String staff, List<String> states, int systemEntries) {
        var entries = history(id); check(entries.size() == states.size(), "complete timeline count");
        long actor = jdbc.queryForObject("select user_id from dbo.users where email=?", Long.class, staff);
        for (int i = 0; i < states.size(); i++) {
            var h = entries.get(i); check(Objects.equals(h.get("old_status"), i == 0 ? null : states.get(i - 1)) && h.get("new_status").equals(states.get(i))
                    && h.get("order_id").equals(id) && Objects.equals(h.get("changed_by_user_id"), i < systemEntries ? null : actor) && h.get("changed_at") != null, "timeline edge/order/system or authenticated staff actor");
        }
    }
    void happy(boolean changeCatalog) throws Exception {
        try (var ignored = cleanup()) {
            Mixed f = mixed(); var catalog = rows("products"); var snapshots = jdbc.queryForList("select oi.* from dbo.order_items oi join dbo.orders o on o.order_id=oi.order_id where o.checkout_id=? order by 1", f.checkout);
            try {
                if (changeCatalog) {
                    for (long id : f.ids()) {
                        long sp = number(((List<?>) aggregate(id).get("items")).stream().map(v -> (Map<?, ?>) v).findFirst().orElseThrow().get("store_product_id"));
                        jdbc.update("update dbo.products set name='CURRENT CATALOG CHANGED' where product_id=(select product_id from dbo.store_products where store_product_id=?)", sp);
                        jdbc.update("update dbo.store_products set price=price+12345 where store_product_id=?", sp);
                    }
                }
                delivery(f); onlineResult(f, true); var onlineReceipt = receipts(f.b);
                pickupReady(f, f.b, 2); pickupComplete(f, f.b, 2); check(receipts(f.b).equals(onlineReceipt), "online receipt unchanged through pickup");
                pickupReady(f, f.c, 4); pickupComplete(f, f.c, 4);
                for (long id : f.ids()) {
                    state(id, "COMPLETED", "PAID"); String root = id == f.a ? "delivery" : "pickup";
                    long store = number(order(id).get("store_id"));
                    var customer = get("/orders/" + id, account(CUSTOMER)); var staff = get("/staff/orders/" + root + "/" + id, account(STAFF.get(store)));
                    check(customer.statusCode() == 200 && staff.statusCode() == 200, "customer/staff completed views");
                    String originalName = snapshots.stream().filter(i -> number(i.get("order_id")) == id).findFirst().orElseThrow().get("product_name").toString();
                    check(customer.body().contains(originalName) && staff.body().contains(originalName) && !customer.body().contains("CURRENT CATALOG CHANGED") && !staff.body().contains("CURRENT CATALOG CHANGED"), "snapshot UI after catalog changes");
                }
                check(jdbc.queryForList("select oi.* from dbo.order_items oi join dbo.orders o on o.order_id=oi.order_id where o.checkout_id=? order by 1", f.checkout).equals(snapshots), "items exact unchanged throughout full checkout");
                var before = snapshot();
                for (long id : f.ids()) check(post(account(STAFF.get(number(order(id).get("store_id")))), "/staff/orders/" + (id == f.a ? "delivery" : "pickup") + "/" + id + "/complete", "pickupCode=" + (code(id) == null ? "" : code(id))).statusCode() == 400, "terminal cannot recollect");
                check(snapshot().equals(before), "terminal exact unchanged");
            } finally {
                if (changeCatalog) for (var p : catalog) jdbc.update("update dbo.products set name=? where product_id=?", p.get("name"), p.get("product_id"));
            }
        }
    }
    void mixedFailure() throws Exception {
        try (var ignored = cleanup()) {
            Mixed f = mixed(); var stock = rows("store_products"); long payment = onlineResult(f, false);
            long sp = number(jdbc.queryForMap("select * from dbo.order_items where order_id=?", f.b).get("store_product_id"));
            for (var prior : stock) {
                var current = jdbc.queryForMap("select * from dbo.store_products where store_product_id=?", prior.get("store_product_id"));
                if (number(prior.get("store_product_id")) == sp) check(number(current.get("quantity")) == number(prior.get("quantity")) + 1, "only failed target restores Q");
                else check(current.equals(prior), "all sibling and unrelated stock exact unchanged");
            }
            var movement = jdbc.queryForMap("select * from dbo.inventory_movements where reference_order_id=? and type='CANCEL_ORDER'", f.b);
            check(movement.get("store_product_id").equals(sp) && number(movement.get("quantity_change")) == 1 && movement.get("staff_id") == null, "cancel FK/change/system actor");
            timeline(f.b, STAFF.get(2L), List.of("PENDING_PAYMENT", "CANCELLED"), 2);
            delivery(f); pickupReady(f, f.c, 4);
            state(f.a, "COMPLETED", "PAID"); state(f.b, "CANCELLED", "FAILED"); state(f.c, "READY_FOR_PICKUP", "UNPAID");
            var before = snapshot();
            for (String action : List.of("prepare", "ready", "complete")) check(post(account(STAFF.get(2L)), "/staff/orders/pickup/" + f.b + "/" + action, "pickupCode=WRONG999").statusCode() == 400, "cancelled fulfillment rejected");
            check(post(account(CUSTOMER), "/orders/" + f.b + "/payments/" + payment + "/success", "").statusCode() == 400, "failed final no success");
            check(post(account(CUSTOMER), "/orders/" + f.b + "/payments/" + payment + "/failure", "").statusCode() == 302, "failure repeat idempotent");
            check(snapshot().equals(before), "failure repeat no duplicate restore"); pickupComplete(f, f.c, 4);
        }
    }
    void security() throws Exception {
        try (var ignored = cleanup()) {
            Mixed f = mixed(); onlineResult(f, true); pickupReady(f, f.b, 2); pickupReady(f, f.c, 4); var before = snapshot();
            String owner = account(CUSTOMER), other = account(OTHER_CUSTOMER);
            check(get("/checkout/" + f.checkout + "?userId=5", other).statusCode() == 404, "checkout ownership");
            for (long id : f.ids()) {
                var denied = get("/orders/" + id + "?userId=5&checkoutId=" + f.checkout, other);
                check(denied.statusCode() == 404 && !denied.body().contains("Phase9 integration receiver") && (code(id) == null || !denied.body().contains(code(id))), "other customer no detail/code/receiver/history/receipt");
                check(get("/orders/" + id + "/payments?userId=5", other).statusCode() == 404, "other customer cannot read receipt");
                check(post(other, "/orders/" + id + "/payments/attempts", FORGED).statusCode() == 404, "forged owner cannot pay");
                for (long store : STAFF.keySet()) if (store != number(order(id).get("store_id"))) {
                    String root = "/staff/orders/" + (id == f.a ? "delivery" : "pickup") + "/" + id;
                    check(get(root + "?storeId=" + store, account(STAFF.get(store))).statusCode() == 404, "all cross-store staff details denied");
                    for (String action : id == f.a ? List.of("prepare", "pack", "ship", "complete") : List.of("prepare", "ready", "complete"))
                        check(post(account(STAFF.get(store)), root + "/" + action, "pickupCode=" + (code(id) == null ? "WRONG999" : code(id)) + "&storeId=" + store).statusCode() == 403, "all cross-store mutations denied even known code");
                }
            }
            for (String cookie : List.of(owner, other)) for (long id : f.ids()) check(!get("/orders?checkoutId=" + f.checkout, cookie).body().contains("id=\"pickup-code\""), "list never exposes stored codes");
            check(snapshot().equals(before), "ownership and store checks zero mutation");
        }
    }
    void csrfAndRoles() throws Exception {
        try (var ignored = cleanup()) {
            Mixed f = mixed(); var before = snapshot(); String owner = account(CUSTOMER), staff = account(STAFF.get(1L)), admin = account("admin@oneshop.vn");
            var paths = Map.of("/orders/" + f.b + "/payments/attempts", owner,
                    "/staff/orders/delivery/" + f.a + "/prepare", staff, "/staff/orders/pickup/" + f.c + "/prepare", account(STAFF.get(4L)));
            for (var entry : paths.entrySet()) {
                String path = entry.getKey(), cookie = entry.getValue(); String bearer = "Bearer " + cookie.substring("ONESHOP_TOKEN=".length());
                check(raw(path, "", "Cookie", cookie).statusCode() == 403, "missing CSRF");
                check(raw(path, "_csrf=WRONG", "Cookie", cookie + "; XSRF-TOKEN=REAL").statusCode() == 403, "wrong CSRF");
                for (String encoded : List.of(path, path.replace("/orders", "/%6frders"), path.replace("/staff", "/%73taff").replace("/pickup", "/%70ickup").replace("/delivery", "/%64elivery")))
                    check(raw(encoded, "", "Authorization", bearer).statusCode() == 403, "Bearer encoded protected form still CSRF");
                check(get(path, cookie).statusCode() == 405, "GET action no mutation");
                check(post("", path, "").statusCode() == 302, "guest denied");
                for (String wrongRole : path.startsWith("/orders") ? List.of(staff, admin) : List.of(owner, admin)) check(post(wrongRole, path, FORGED).statusCode() == 403, "role matrix mutation");
            }
            for (String wrongRole : List.of(staff, admin)) check(get("/orders/" + f.b, wrongRole).statusCode() == 403, "customer detail role");
            for (String wrongRole : List.of(owner, admin)) for (String root : List.of("delivery", "pickup")) check(get("/staff/orders/" + root, wrongRole).statusCode() == 403, "staff list role");
            check(post(staff, "/staff/orders/pickup/" + f.a + "/prepare", FORGED).statusCode() == 400, "common prepare edge respects type");
            check(post(account(STAFF.get(4L)), "/staff/orders/delivery/" + f.c + "/prepare", FORGED).statusCode() == 400, "opposite prepare type");
            check(snapshot().equals(before), "security rejects exact zero changes");
        }
    }
    void multiAssignment() throws Exception {
        try (var ignored = cleanup()) {
            Mixed f = mixed(); String staff = account(STAFF.get(1L)); long actor = jdbc.queryForObject("select user_id from dbo.users where email=?", Long.class, STAFF.get(1L));
            long max = jdbc.queryForObject("select max(assignment_id) from dbo.staff_store_assignments", Long.class);
            try {
                jdbc.update("insert into dbo.staff_store_assignments(user_id,store_id,status,assigned_at) values (?,2,'ACTIVE',SYSDATETIME()),(?,4,'INACTIVE',SYSDATETIME())", actor, actor);
                var page = get("/staff/orders/pickup", staff); check(page.statusCode() == 200 && page.body().contains("data-order-id=\"" + f.b + "\"") && !page.body().contains("data-order-id=\"" + f.c + "\""), "active assignment list only");
                onlineResult(f, true); mutate(f, f.b, "/staff/orders/pickup/" + f.b + "/prepare", STAFF.get(1L), FORGED);
                check(number(history(f.b).get(2).get("changed_by_user_id")) == actor, "second active store allowed with actual actor");
                var before = snapshot(); check(get("/staff/orders/pickup/" + f.c, staff).statusCode() == 404 && post(staff, "/staff/orders/pickup/" + f.c + "/prepare", FORGED).statusCode() == 403, "inactive assignment no scope");
                check(snapshot().equals(before), "inactive assignment zero mutation");
            } finally { jdbc.update("delete from dbo.staff_store_assignments where assignment_id>? and user_id=?", max, actor); }
        }
    }
    void invalidCombination(String type, String method) throws Exception {
        try (var ignored = cleanup()) {
            long line = add(1, "COCOON-SERUM-30"); var before = snapshot();
            var response = post(account(CUSTOMER), "/checkout", "cartItemIds=" + line + "&" + group(0, 1, type, method));
            check(response.statusCode() == 400 && snapshot().equals(before), "business invalid combination rejected atomically");
            long checkout = place(List.of(line), List.of(group(0, 1, "DELIVERY", "COD")));
            long id = jdbc.queryForObject("select order_id from dbo.orders where checkout_id=?", Long.class, checkout);
            var valid = snapshot(); boolean rejected = false;
            try { jdbc.update("update dbo.orders set fulfillment_type=?,payment_method=? where order_id=?", type, method, id); }
            catch (org.springframework.dao.DataIntegrityViolationException expected) { rejected = true; }
            check(rejected && snapshot().equals(valid), "SQL Server CHECK also rejects invalid pairing without disabling constraints");
        }
    }
}
