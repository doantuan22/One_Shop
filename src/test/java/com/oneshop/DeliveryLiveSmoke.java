package com.oneshop;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.Reader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Standalone HTTP/JDBC check against the packaged dev JAR; run on seed DB without other writers. */
public final class DeliveryLiveSmoke {
    private static final String CUSTOMER = "khachhang1@example.com";
    private static final String STAFF = "staff.thuduc@oneshop.vn";
    private static final String ROOT = "/staff/orders/delivery";
    private static final List<String> ACTIONS = List.of("prepare", "pack", "ship", "complete");
    private static final List<String> FROM = List.of("CONFIRMED", "PREPARING", "PACKED", "SHIPPING");
    private static final List<String> TO = List.of("PREPARING", "PACKED", "SHIPPING", "COMPLETED");
    private static final List<String> TABLES = List.of("orders", "order_items", "payments", "order_status_history",
            "inventory_movements", "store_products", "checkout_sessions", "cart_items", "carts");
    private final JdbcTemplate jdbc;
    private final String base;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private DeliveryLiveSmoke(JdbcTemplate jdbc, String base) { this.jdbc = jdbc; this.base = base; }

    public static void main(String[] args) throws Exception {
        Properties config = new Properties();
        if (Files.exists(Path.of(".env"))) {
            try (Reader reader = Files.newBufferedReader(Path.of(".env"), StandardCharsets.UTF_8)) { config.load(reader); }
        }
        String url = "jdbc:sqlserver://" + value(config, "DB_HOST", "localhost") + ":" + value(config, "DB_PORT", "1433")
                + ";databaseName=" + value(config, "DB_NAME", "oneshop") + ";encrypt=true;trustServerCertificate="
                + value(config, "DB_TRUST_SERVER_CERTIFICATE", "false");
        DriverManagerDataSource source = new DriverManagerDataSource(url, value(config, "DB_USERNAME", ""), value(config, "DB_PASSWORD", ""));
        source.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        new DeliveryLiveSmoke(new JdbcTemplate(source), args.length == 0 ? "http://localhost:18081" : args[0]).run();
    }

    private static String value(Properties config, String name, String fallback) {
        return System.getenv().getOrDefault(name, config.getProperty(name, fallback));
    }

    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }

    private Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : TABLES) result.put(table, jdbc.queryForList("select * from dbo." + table + " order by 1"));
        return result;
    }

    private static Map<String, Integer> counts(Map<String, List<Map<String, Object>>> rows) {
        Map<String, Integer> result = new LinkedHashMap<>();
        rows.forEach((table, data) -> result.put(table, data.size())); return result;
    }

    private void run() throws Exception {
        var before = snapshot();
        SeedGuard guard = new SeedGuard(jdbc); guard.remember();
        try {
            check(get("/health", "").statusCode() == 200, "packaged dev app health");
            String customer = login(CUSTOMER), staff = login(STAFF), otherStaff = login("staff.govap@oneshop.vn");
            long mixed = place(customer, List.of(add(customer, "COCOON-SERUM-30", 1), add(customer, "INNI-TONER-200", 2),
                    add(customer, "INNI-CLEANS-120", 4)), List.of(group(0, 1, "DELIVERY", "COD"),
                    group(1, 2, "DELIVERY", "COD"), group(2, 4, "STORE_PICKUP", "PAY_AT_STORE")));
            var mixedOrders = jdbc.queryForList("select * from dbo.orders where checkout_id=? order by order_id", mixed);
            long cod = ((Number) mixedOrders.stream().filter(row -> ((Number) row.get("store_id")).longValue() == 1).findFirst().orElseThrow().get("order_id")).longValue();
            var untouched = mixedOrders.stream().filter(row -> ((Number) row.get("order_id")).longValue() != cod).toList();
            var list = get(ROOT + "?storeId=2", staff);
            check(list.statusCode() == 200 && list.body().contains("data-order-id=\"" + cod + "\"")
                    && list.body().contains("data-layout=\"staff\"") && list.body().contains("href=\"" + ROOT + "\""), "Staff list and working sidebar link");
            for (var row : untouched) check(!list.body().contains("data-order-id=\"" + row.get("order_id") + "\""), "list is scoped and DELIVERY-only");
            flow(staff, cod, true);
            for (var row : untouched) check(jdbc.queryForMap("select * from dbo.orders where order_id=?", row.get("order_id")).equals(row), "neighbor Orders untouched");
            System.out.println("Live TC-09 COD / four Staff histories / one receipt / snapshot / stock / movement / duplicate: PASS");

            long online = create(customer, "DELIVERY", "ONLINE");
            check(post(customer, "/orders/" + online + "/payments/attempts", "amount=1").statusCode() == 302, "ONLINE start");
            long payment = jdbc.queryForObject("select payment_id from dbo.payments where order_id=?", Long.class, online);
            String success = "/orders/" + online + "/payments/" + payment + "/success";
            check(post(customer, success, "").statusCode() == 302, "ONLINE SUCCESS");
            var oldReceipt = receipts(online);
            flow(staff, online, false);
            check(receipts(online).equals(oldReceipt), "ONLINE completion preserves old receipt, paid_at and code");
            check(post(customer, success, "").statusCode() == 302 && receipts(online).equals(oldReceipt), "ONLINE repeated result after COMPLETED");
            System.out.println("Live ONLINE paid DELIVERY / unchanged receipt / terminal / Staff actor: PASS");

            long pickup = create(customer, "STORE_PICKUP", "PAY_AT_STORE");
            long pending = create(customer, "DELIVERY", "ONLINE");
            var protectedRows = snapshot();
            check(get(ROOT + "/" + cod, otherStaff).statusCode() == 404, "other Staff cannot read detail");
            for (String action : ACTIONS) {
                check(post(otherStaff, ROOT + "/" + cod + "/" + action, "storeId=1").statusCode() == 403, "other Store cannot act");
                check(post(staff, ROOT + "/" + pickup + "/" + action, "").statusCode() == 400, "own STORE_PICKUP rejects DELIVERY action");
                check(post(staff, ROOT + "/" + pending + "/" + action, "").statusCode() == 400, "ONLINE pending cannot fulfill");
                check(raw(ROOT + "/" + pending + "/" + action, "", "Cookie", staff).statusCode() == 403, "missing CSRF");
                check(raw(ROOT + "/" + pending + "/" + action, "", "Authorization", "Bearer " + staff.substring("ONESHOP_TOKEN=".length())).statusCode() == 403, "Bearer also requires CSRF");
                for (String encodedRoot : List.of("/%73taff/orders/delivery", "/staff/%6frders/delivery", "/staff/orders/%64elivery")) {
                    check(raw(encodedRoot + "/" + pending + "/" + action, "", "Authorization", "Bearer " + staff.substring("ONESHOP_TOKEN=".length())).statusCode() == 403,
                            "encoded DELIVERY routes still require CSRF");
                }
                check(get(ROOT + "/" + pending + "/" + action, staff).statusCode() == 405, "GET cannot mutate");
            }
            check(!get(ROOT + "/" + pending, staff).body().contains(ROOT + "/" + pending + "/prepare"), "pending UI hides prepare");
            check(raw("/%6frders/" + pending + "/payments/attempts", "", "Authorization", "Bearer " + customer.substring("ONESHOP_TOKEN=".length())).statusCode() == 403,
                    "encoded ONLINE payment form also requires CSRF");
            String admin = login("admin@oneshop.vn");
            for (String account : List.of(customer, admin)) {
                check(get(ROOT, account).statusCode() == 403, "Customer/Admin cannot use Staff UI");
                for (String action : ACTIONS) check(post(account, ROOT + "/" + pending + "/" + action, "").statusCode() == 403, "Customer/Admin cannot mutate");
            }
            check(get(ROOT, "").statusCode() == 302, "guest redirected to login");
            check(post("", ROOT + "/" + pending + "/prepare", "").statusCode() == 302, "guest cannot prepare");
            check(protectedRows.equals(snapshot()), "all rejected requests leave rows exactly unchanged");
            System.out.println("Live pending ONLINE / cross Store / pickup isolation / roles / CSRF cookie+Bearer / GET guards: PASS");
        } finally {
            guard.restore();
            var after = snapshot();
            check(after.equals(before), "exact cleanup including all ids and timestamps");
            System.out.println("Cleanup exact rows: PASS; before=" + counts(before) + "; after=" + counts(after));
        }
    }

    private void flow(String staff, long id, boolean cod) throws Exception {
        var stock = jdbc.queryForList("select * from dbo.store_products order by 1");
        var movements = jdbc.queryForList("select * from dbo.inventory_movements order by 1");
        var items = jdbc.queryForList("select * from dbo.order_items where order_id=?", id);
        int initialHistory = jdbc.queryForObject("select count(*) from dbo.order_status_history where order_id=?", Integer.class, id);
        long actor = jdbc.queryForObject("select user_id from dbo.users where email=?", Long.class, STAFF);
        for (int index = 0; index < ACTIONS.size(); index++) {
            String path = ROOT + "/" + id + "/" + ACTIONS.get(index);
            var detail = get(ROOT + "/" + id, staff);
            check(detail.statusCode() == 200 && detail.body().contains(path) && detail.body().contains("name=\"_csrf\""), "detail renders next fixed action");
            check(post(staff, path, "amount=1&method=PAY_AT_STORE&storeId=2&userId=1&targetStatus=CANCELLED&transactionCode=HACK").statusCode() == 302, "Staff action succeeds");
            var order = jdbc.queryForMap("select * from dbo.orders where order_id=?", id);
            check(order.get("order_status").equals(TO.get(index)), "fixed target status");
            check(order.get("payment_status").equals(cod && index < 3 ? "UNPAID" : "PAID"), "payment lifecycle");
            check(receipts(id).size() == (cod && index < 3 ? 0 : 1), "no early or duplicate COD receipt");
            var afterAction = snapshot();
            check(post(staff, path, "").statusCode() == 400 && afterAction.equals(snapshot()), "double click rejects without rewriting receipt/history");
            check(jdbc.queryForList("select * from dbo.store_products order by 1").equals(stock), "fulfillment never changes stock");
            check(jdbc.queryForList("select * from dbo.inventory_movements order by 1").equals(movements), "fulfillment never creates movement");
            check(jdbc.queryForList("select * from dbo.order_items where order_id=?", id).equals(items), "item snapshots unchanged");
        }
        var entries = jdbc.queryForList("select * from dbo.order_status_history where order_id=? order by history_id", id);
        check(entries.size() == initialHistory + 4, "exactly four fulfillment histories");
        for (int index = 0; index < 4; index++) {
            var entry = entries.get(initialHistory + index);
            check(entry.get("old_status").equals(FROM.get(index)) && entry.get("new_status").equals(TO.get(index))
                    && ((Number) entry.get("changed_by_user_id")).longValue() == actor && entry.get("changed_at") != null, "Staff history actor and edge");
        }
        if (cod) {
            var receipt = receipts(id).get(0);
            check(receipt.get("status").equals("SUCCESS") && receipt.get("method").equals("COD") && receipt.get("paid_at") != null
                    && receipt.get("transaction_code").toString().matches("PAY-[0-9a-f-]{36}")
                    && receipt.get("amount").equals(jdbc.queryForObject("select total_amount from dbo.orders where order_id=?", java.math.BigDecimal.class, id)), "COD receipt fields from snapshot");
        }
    }

    private List<Map<String, Object>> receipts(long id) { return jdbc.queryForList("select * from dbo.payments where order_id=? order by payment_id", id); }

    private long create(String customer, String type, String method) throws Exception {
        long checkout = place(customer, List.of(add(customer, "COCOON-SERUM-30", 1)), List.of(group(0, 1, type, method)));
        return jdbc.queryForObject("select order_id from dbo.orders where checkout_id=?", Long.class, checkout);
    }

    private long add(String customer, String sku, long store) throws Exception {
        long stock = jdbc.queryForObject("select sp.store_product_id from dbo.store_products sp join dbo.products p on p.product_id=sp.product_id "
                + "where p.sku=? and sp.store_id=?", Long.class, sku, store);
        check(post(customer, "/cart/items", "storeProductId=" + stock + "&quantity=1").statusCode() == 302, "cart add");
        return jdbc.queryForObject("select ci.cart_item_id from dbo.cart_items ci join dbo.carts c on c.cart_id=ci.cart_id "
                + "join dbo.users u on u.user_id=c.user_id where u.email=? and ci.store_product_id=?", Long.class, CUSTOMER, stock);
    }

    private static String group(int index, long store, String type, String method) {
        String prefix = "groups[" + index + "].";
        return prefix + "storeId=" + store + "&" + prefix + "fulfillmentType=" + type + "&" + prefix + "paymentMethod=" + method
                + "&" + prefix + "receiverName=" + enc("Người nhận smoke") + "&" + prefix + "receiverPhone=0912345678"
                + (type.equals("DELIVERY") ? "&" + prefix + "shippingAddress=1+Smoke+Street" : "");
    }

    private long place(String customer, List<Long> lines, List<String> groups) throws Exception {
        String body = String.join("&", lines.stream().map(id -> "cartItemIds=" + id).toList()) + "&" + String.join("&", groups);
        var response = post(customer, "/checkout", body);
        check(response.statusCode() == 302, "Phase 8 checkout");
        Matcher id = Pattern.compile("/checkout/(\\d+)").matcher(response.headers().firstValue("Location").orElseThrow());
        check(id.find(), "checkout redirect"); return Long.parseLong(id.group(1));
    }

    private String login(String email) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/login")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\"OneShop@123\"}")).build(), HttpResponse.BodyHandlers.ofString());
        check(response.statusCode() == 200, "seed login");
        Matcher token = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(response.body());
        check(token.find(), "login token"); return "ONESHOP_TOKEN=" + token.group(1);
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).header("Cookie", cookie).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(String cookie, String path, String body) throws Exception {
        var page = get("/login", "");
        Matcher csrf = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body()); check(csrf.find(), "csrf form token");
        String tokenCookie = page.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN="))
                .map(c -> c.split(";")[0]).findFirst().orElseThrow();
        return raw(path, body + "&_csrf=" + enc(csrf.group(1)), "Cookie", cookie + "; " + tokenCookie);
    }

    private HttpResponse<String> raw(String path, String body, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (headers.length > 0) request.headers(headers);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
