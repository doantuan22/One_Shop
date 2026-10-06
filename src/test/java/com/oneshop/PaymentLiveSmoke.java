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

/** Standalone HTTP/JDBC verification against the separately running dev app (no test mocks or Spring test context).
 * Run only on the seeded development DB without other writers. Always restores and compares every touched row. */
public final class PaymentLiveSmoke {
    private static final String ALICE = "khachhang1@example.com";
    private static final List<String> TABLES = List.of("checkout_sessions", "orders", "order_items", "payments",
            "inventory_movements", "order_status_history", "store_products", "cart_items", "carts");
    private final JdbcTemplate jdbc;
    private final String base;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private PaymentLiveSmoke(JdbcTemplate jdbc, String base) { this.jdbc = jdbc; this.base = base; }

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
        PaymentLiveSmoke smoke = new PaymentLiveSmoke(new JdbcTemplate(source), args.length == 0 ? "http://localhost:18081" : args[0]);
        smoke.run();
    }

    private static String value(Properties config, String name, String fallback) {
        return System.getenv().getOrDefault(name, config.getProperty(name, fallback));
    }
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
    private Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : TABLES) result.put(table, jdbc.queryForList("select * from dbo." + table + " order by 1"));
        return result;
    }
    private Map<String, Integer> counts(Map<String, List<Map<String, Object>>> rows) {
        Map<String, Integer> result = new LinkedHashMap<>();
        rows.forEach((table, data) -> result.put(table, data.size()));
        return result;
    }

    private void run() throws Exception {
        var before = snapshot();
        SeedGuard guard = new SeedGuard(jdbc);
        guard.remember();
        try {
            check(get("/health", "").statusCode() == 200, "dev app health");
            String alice = login(ALICE);
            String bob = login("khachhang2@example.com");
            long serum = sp("COCOON-SERUM-30", 1);
            long toner = sp("INNI-TONER-200", 2);
            long foam = sp("INNI-CLEANS-120", 4);
            long cheek = sp("BBIA-CHEEK-08", 1);
            long checkout = place(alice, List.of(add(alice, serum), add(alice, toner), add(alice, foam)),
                    List.of(group(0, 1, "ONLINE"), group(1, 2, "COD"), group(2, 4, "PAY_AT_STORE")));
            List<Map<String, Object>> orders = jdbc.queryForList("select * from dbo.orders where checkout_id=? order by order_id", checkout);
            long online = ((Number) orders.stream().filter(row -> row.get("payment_method").equals("ONLINE")).findFirst().orElseThrow().get("order_id")).longValue();
            var otherOrders = orders.stream().filter(row -> !row.get("payment_method").equals("ONLINE")).toList();
            long success = start(alice, online);
            check(start(alice, online) == success, "double start reuses pending");
            check(get(path(online), bob).statusCode() == 404, "other customer cannot read");
            check(postRaw(path(online) + "/" + success + "/success", alice, "").statusCode() == 403, "CSRF required");
            check(post(bob, path(online) + "/" + success + "/failure", "").statusCode() == 404, "other customer cannot fail");
            var stockAtPayment = jdbc.queryForList("select * from dbo.store_products order by store_product_id");
            var ledgerAtPayment = jdbc.queryForList("select * from dbo.inventory_movements order by movement_id");
            check(post(alice, path(online) + "/" + success + "/success", "amount=1&status=FAILED").statusCode() == 302, "success result");
            check(jdbc.queryForList("select * from dbo.store_products order by store_product_id").equals(stockAtPayment), "success stock unchanged");
            check(jdbc.queryForList("select * from dbo.inventory_movements order by movement_id").equals(ledgerAtPayment), "success movements unchanged");
            checkState(online, "CONFIRMED", "PAID");
            var record = jdbc.queryForMap("select * from dbo.payments where payment_id=?", success);
            check(record.get("status").equals("SUCCESS") && record.get("paid_at") != null && record.get("transaction_code").toString().startsWith("PAY-"), "success audit fields");
            check(record.get("amount").equals(jdbc.queryForObject("select total_amount from dbo.orders where order_id=?", java.math.BigDecimal.class, online)), "backend amount");
            var terminal = snapshot();
            check(post(alice, path(online) + "/" + success + "/success", "").statusCode() == 302, "double success");
            check(snapshot().equals(terminal), "double success writes nothing");
            check(post(alice, path(online) + "/" + success + "/failure", "").statusCode() == 400, "success cannot fail");
            check(jdbc.queryForList("select * from dbo.orders where checkout_id=? and order_id<>? order by order_id", checkout, online).equals(otherOrders), "mixed methods isolated");
            for (var row : otherOrders) {
                long id = ((Number) row.get("order_id")).longValue();
                checkState(id, "CONFIRMED", "UNPAID");
                check(post(alice, path(id) + "/attempts", "").statusCode() == 400, "offline method refused");
                check(jdbc.queryForObject("select count(*) from dbo.payments where order_id=?", Integer.class, id) == 0, "offline payment not created");
            }
            checkHistory(online, "CONFIRMED");
            check(get(path(online), alice).body().contains("Đã thanh toán"), "success view");
            System.out.println("Live SUCCESS / amount / audit / isolation / duplicate / ownership / CSRF / offline methods: PASS");

            var stockBeforeFailureOrder = jdbc.queryForList("select store_product_id,quantity from dbo.store_products order by store_product_id");
            long failedCheckout = place(alice, List.of(add(alice, serum), add(alice, cheek)), List.of(group(0, 1, "ONLINE")));
            long failedOrder = jdbc.queryForObject("select order_id from dbo.orders where checkout_id=?", Long.class, failedCheckout);
            long failedPayment = start(alice, failedOrder);
            check(post(alice, path(failedOrder) + "/" + failedPayment + "/failure", "").statusCode() == 302, "failed result");
            checkState(failedOrder, "CANCELLED", "FAILED");
            check(jdbc.queryForList("select store_product_id,quantity from dbo.store_products order by store_product_id").equals(stockBeforeFailureOrder), "failure restores stock exactly");
            var items = jdbc.queryForList("select store_product_id,quantity from dbo.order_items where order_id=? order by store_product_id", failedOrder);
            var restored = jdbc.queryForList("select store_product_id,quantity_change as quantity from dbo.inventory_movements "
                    + "where reference_order_id=? and type='CANCEL_ORDER' order by store_product_id", failedOrder);
            check(items.equals(restored), "every item has exact CANCEL_ORDER movement");
            record = jdbc.queryForMap("select * from dbo.payments where payment_id=?", failedPayment);
            check(record.get("status").equals("FAILED") && record.get("paid_at") == null, "failed fields");
            checkHistory(failedOrder, "CANCELLED");
            terminal = snapshot();
            check(post(alice, path(failedOrder) + "/" + failedPayment + "/failure", "").statusCode() == 302, "double failure");
            check(snapshot().equals(terminal), "double failure restores once");
            check(post(alice, path(failedOrder) + "/attempts", "").statusCode() == 400, "cancelled cannot retry");
            check(post(alice, path(failedOrder) + "/" + failedPayment + "/success", "").statusCode() == 400, "failed cannot succeed");
            check(get(path(failedOrder), alice).body().contains("Đã hủy"), "failure view");
            for (String email : List.of("staff.thuduc@oneshop.vn", "admin@oneshop.vn")) {
                String cookie = login(email);
                check(get(path(failedOrder), cookie).statusCode() == 403, "staff/admin client view forbidden");
                check(post(cookie, path(failedOrder) + "/attempts", "").statusCode() == 403, "staff/admin client action forbidden");
            }
            System.out.println("Live FAILED / all items restored / CANCEL_ORDER / history / duplicate / terminal guards / roles: PASS");
        } finally {
            guard.restore();
            var after = snapshot();
            check(after.equals(before), "cleanup must restore every touched row, including stock/cart timestamps");
            System.out.println("Cleanup exact rows: PASS; before=" + counts(before) + "; after=" + counts(after));
        }
    }

    private void checkState(long id, String state, String payment) {
        var row = jdbc.queryForMap("select order_status,payment_status from dbo.orders where order_id=?", id);
        check(row.get("order_status").equals(state) && row.get("payment_status").equals(payment), "order/payment state");
    }
    private void checkHistory(long id, String next) {
        var rows = jdbc.queryForList("select old_status,new_status,changed_by_user_id from dbo.order_status_history where order_id=? order by history_id", id);
        check(rows.size() == 2 && rows.get(0).get("old_status") == null && rows.get(1).get("old_status").equals("PENDING_PAYMENT")
                && rows.get(1).get("new_status").equals(next) && rows.get(1).get("changed_by_user_id") == null, "system history");
    }
    private long sp(String sku, long store) {
        return jdbc.queryForObject("select sp.store_product_id from dbo.store_products sp join dbo.products p on p.product_id=sp.product_id "
                + "where p.sku=? and sp.store_id=?", Long.class, sku, store);
    }
    private static String path(long id) { return "/orders/" + id + "/payments"; }
    private long add(String cookie, long sp) throws Exception {
        check(post(cookie, "/cart/items", "storeProductId=" + sp + "&quantity=1").statusCode() == 302, "add cart");
        return jdbc.queryForObject("select ci.cart_item_id from dbo.cart_items ci join dbo.carts c on c.cart_id=ci.cart_id "
                + "join dbo.users u on u.user_id=c.user_id where u.email=? and ci.store_product_id=?", Long.class, ALICE, sp);
    }
    private static String group(int index, long storeId, String method) {
        String p = "groups[" + index + "].";
        return p + "storeId=" + storeId + "&" + p + "fulfillmentType=" + (method.equals("COD") ? "DELIVERY" : "STORE_PICKUP")
                + "&" + p + "paymentMethod=" + method + "&" + p + "receiverName=Smoke&" + p + "receiverPhone=0912345678"
                + (method.equals("COD") ? "&" + p + "shippingAddress=1+Smoke+Street" : "");
    }
    private long place(String cookie, List<Long> lines, List<String> groups) throws Exception {
        String body = String.join("&", lines.stream().map(id -> "cartItemIds=" + id).toList()) + "&" + String.join("&", groups);
        var response = post(cookie, "/checkout", body);
        check(response.statusCode() == 302, "Phase 8 checkout");
        Matcher id = Pattern.compile("/checkout/(\\d+)").matcher(response.headers().firstValue("Location").orElse(""));
        check(id.find(), "checkout redirect");
        return Long.parseLong(id.group(1));
    }
    private long start(String cookie, long id) throws Exception {
        check(post(cookie, path(id) + "/attempts", "amount=1&method=COD&userId=6&status=SUCCESS").statusCode() == 302, "start pending");
        return jdbc.queryForObject("select payment_id from dbo.payments where order_id=?", Long.class, id);
    }
    private String login(String email) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + email + "\",\"password\":\"OneShop@123\"}")).build(), HttpResponse.BodyHandlers.ofString());
        Matcher token = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(response.body());
        check(token.find(), "seed login");
        return "ONESHOP_TOKEN=" + token.group(1);
    }
    private HttpResponse<String> get(String path, String cookie) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).header("Cookie", cookie).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    private HttpResponse<String> post(String cookie, String path, String body) throws Exception {
        var page = get("/login", "");
        Matcher csrf = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        check(csrf.find(), "csrf token");
        String tokenCookie = page.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN="))
                .map(c -> c.split(";")[0]).findFirst().orElseThrow();
        return postRaw(path, cookie + "; " + tokenCookie, body + "&_csrf=" + URLEncoder.encode(csrf.group(1), StandardCharsets.UTF_8));
    }
    private HttpResponse<String> postRaw(String path, String cookie, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", cookie).POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
