package com.oneshop;

import com.oneshop.dto.request.AddCartItemRequest;
import com.oneshop.entity.CartItem;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.CartService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 8 end to end: real Tomcat (security filters, CSRF, form binding, SiteMesh) on the real SQL Server, requests
 * made the way a browser makes them (TC-06, TC-08, TC-14 through HTTP).
 *
 * <p>Skipped without a configured database. These tests commit, so {@link SeedGuard} records the state before each
 * test and puts it back afterwards.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class CheckoutHttpDatabaseIntegrationTest {

    private static final String SEED_PASSWORD = "OneShop@123";
    private static final String ALICE = "khachhang1@example.com";   // empty cart in the seed data
    private static final String BOB = "khachhang2@example.com";     // 3 lines at 3 Stores
    private static final String FORM = "application/x-www-form-urlencoded";

    @LocalServerPort
    private int port;

    @Autowired
    private CartService cartService;

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

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final Map<String, String> tokens = new HashMap<>();
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

    private HttpResponse<String> get(String path, String... headers) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "text/html").GET();
        if (headers.length > 0) {
            request.headers(headers);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(String path, String contentType, String body, String... headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", contentType.startsWith("application/json") ? "application/json" : "text/html")
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (headers.length > 0) {
            request.headers(headers);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String login(String email) throws IOException, InterruptedException {
        if (!tokens.containsKey(email)) {
            HttpResponse<String> login = post("/api/auth/login", "application/json",
                    "{\"email\":\"" + email + "\",\"password\":\"" + SEED_PASSWORD + "\"}");
            Matcher token = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(login.body());
            assertThat(token.find()).as("login of " + email).isTrue();
            tokens.put(email, token.group(1));
        }
        return "ONESHOP_TOKEN=" + tokens.get(email);
    }

    /** POST of a form as the logged-in browser sends it: JWT cookie + CSRF field and cookie. */
    private HttpResponse<String> postAs(String email, String path, String body) throws IOException, InterruptedException {
        String jwt = login(email);
        HttpResponse<String> page = get("/", "Cookie", jwt);
        Matcher field = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertThat(field.find()).isTrue();
        String csrfCookie = page.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("XSRF-TOKEN=")).map(c -> c.split(";")[0]).findFirst().orElseThrow();
        return post(path, FORM, body + "&_csrf=" + field.group(1), "Cookie", jwt + "; " + csrfCookie);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

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

    /** Form fields of one Store block, as the checkout page names them. */
    private static String group(int index, Long storeId, String fulfillment, String payment, String address) {
        String prefix = enc("groups[" + index + "].");
        return prefix + "storeId=" + storeId + "&" + prefix + "fulfillmentType=" + fulfillment + "&" + prefix
                + "paymentMethod=" + payment + "&" + prefix + "receiverName=" + enc("Phạm Thu Hà") + "&" + prefix
                + "receiverPhone=0911111111" + (address == null ? "" : "&" + prefix + "shippingAddress=" + enc(address));
    }

    private static String ids(Long... cartItemIds) {
        return List.of(cartItemIds).stream().map(id -> "cartItemIds=" + id).collect(Collectors.joining("&"));
    }

    private List<Map<String, Object>> ordersOf(long checkoutId) {
        return jdbc.queryForList("select o.order_id, s.code as store, o.fulfillment_type, o.payment_method, o.payment_status, "
                + "o.order_status, o.total_amount, o.shipping_address, o.receiver_name, u.email, o.pickup_code "
                + "from dbo.orders o join dbo.stores s on s.store_id = o.store_id join dbo.users u on u.user_id = o.user_id "
                + "where o.checkout_id = ? order by s.code", checkoutId);
    }

    private static long checkoutIdOf(HttpResponse<String> placed) {
        Matcher id = Pattern.compile("/checkout/(\\d+)\\?success=placed$").matcher(placed.headers().firstValue("Location").orElse(""));
        assertThat(id.find()).as("redirect to the created checkout, got " + placed.statusCode() + " "
                + placed.headers().firstValue("Location").orElse("") + errorOf(placed)).isTrue();
        return Long.parseLong(id.group(1));
    }

    private static String errorOf(HttpResponse<String> response) {
        Matcher error = Pattern.compile("id=\"(?:checkout|cart)-error\"[^>]*>([^<]*)<").matcher(response.body());
        return error.find() ? " [" + error.group(1).trim() + "]" : "";
    }

    private static int count(String text, String token) {
        return text.split(Pattern.quote(token), -1).length - 1;
    }

    // ================================================================= who may check out

    @Test
    void onlyCustomersReachCheckout() throws Exception {
        String body = ids(1L) + "&" + group(0, 1L, "DELIVERY", "COD", "x");

        assertThat(get("/checkout?cartItemIds=1").statusCode()).isEqualTo(302);
        assertThat(get("/checkout/1").statusCode()).isEqualTo(302);
        for (String account : List.of("staff.thuduc@oneshop.vn", "admin@oneshop.vn")) {
            assertThat(get("/checkout?cartItemIds=1", "Cookie", login(account)).statusCode()).as(account).isEqualTo(403);
            assertThat(get("/checkout/1", "Cookie", login(account)).statusCode()).as(account).isEqualTo(403);
            assertThat(postAs(account, "/checkout", body).statusCode()).as(account).isEqualTo(403);
        }
        // a customer without the CSRF token
        Long line = add(ALICE, sp("COCOON-SERUM-30", "OS-THUDUC"), 1);
        Map<String, Long> before = guard.counts();
        HttpResponse<String> noToken = post("/checkout", FORM,
                ids(line) + "&" + group(0, storeId("OS-THUDUC"), "DELIVERY", "COD", "12 Võ Văn Ngân"), "Cookie", login(ALICE));
        assertThat(noToken.statusCode()).isEqualTo(403);
        assertThat(guard.counts()).isEqualTo(before);
        assertThat(rowsOf(ALICE)).hasSize(1);
    }

    // ================================================================= the checkout page

    @Test
    void checkoutPageShowsOneBlockPerStoreWithItsOwnChoicesAndNoPriceFields() throws Exception {
        List<Long> lines = rowsOf(BOB).stream().map(CartItem::getId).toList();

        HttpResponse<String> response = get("/checkout?" + ids(lines.toArray(Long[]::new)), "Cookie", login(BOB));

        assertThat(response.statusCode()).isEqualTo(200);
        String html = response.body();
        assertThat(count(html, "os-checkout-group\"")).isEqualTo(3);
        assertThat(html).contains("OneShop Thủ Đức", "OneShop Gò Vấp", "OneShop Quận 7", "828.000 ₫", "data-layout=\"client\"");
        // every block has its own fulfillment and payment fields
        for (int i = 0; i < 3; i++) {
            assertThat(html).contains("name=\"groups[" + i + "].storeId\"", "name=\"groups[" + i + "].fulfillmentType\"",
                    "name=\"groups[" + i + "].paymentMethod\"", "name=\"groups[" + i + "].receiverName\"",
                    "name=\"groups[" + i + "].receiverPhone\"", "name=\"groups[" + i + "].shippingAddress\"");
        }
        assertThat(count(html, "value=\"DELIVERY\"")).isEqualTo(3);
        assertThat(count(html, "value=\"STORE_PICKUP\"")).isEqualTo(3);
        assertThat(count(html, "value=\"COD\"")).isEqualTo(3);
        assertThat(count(html, "value=\"PAY_AT_STORE\"")).isEqualTo(3);
        assertThat(count(html, "value=\"ONLINE\"")).isEqualTo(3);
        // the chosen lines travel as ids, receiver details are suggested from the default address
        for (Long line : lines) {
            assertThat(html).contains("name=\"cartItemIds\" value=\"" + line + "\"");
        }
        assertThat(html).contains("value=\"Đặng Quốc Khánh\"", "value=\"0922222222\"", "45 Quang Trung");
        // nothing the server must not trust is a form field
        List<String> fieldNames = Pattern.compile("<(?:input|textarea|select)[^>]*name=\"([^\"]+)\"").matcher(html)
                .results().map(m -> m.group(1).replaceAll("\\[\\d+\\]", "[]")).distinct().toList();
        assertThat(fieldNames).containsExactlyInAnyOrder("_csrf", "cartItemIds", "groups[].storeId", "groups[].fulfillmentType",
                "groups[].paymentMethod", "groups[].receiverName", "groups[].receiverPhone", "groups[].shippingAddress");
    }

    @Test
    void aStoreThatDoesNotDeliverOffersPickupOnly() throws Exception {
        Long line = add(ALICE, sp("INNI-CLEANS-120", "OS-QUAN10"), 1);

        String html = get("/checkout?" + ids(line), "Cookie", login(ALICE)).body();

        assertThat(html).contains("OneShop Quận 10", "Chi nhánh này không giao hàng tận nơi.");
        assertThat(count(html, "value=\"DELIVERY\"")).isZero();
        assertThat(count(html, "value=\"STORE_PICKUP\"")).isEqualTo(1);
    }

    @Test
    void linesThatCannotBeCheckedOutLeadBackToTheCartWithTheReason() throws Exception {
        Long bobsLine = rowsOf(BOB).get(0).getId();

        for (String query : List.of("", "?cartItemIds=" + bobsLine, "?cartItemIds=999999999")) {
            HttpResponse<String> response = get("/checkout" + query, "Cookie", login(ALICE));
            assertThat(response.statusCode()).as(query).isEqualTo(400);
            assertThat(response.body()).as(query).contains("id=\"cart-error\"").doesNotContain("os-checkout-group");
        }
    }

    // ================================================================= TC-06 + TC-08 through the form

    @Test
    void tc06_tc08_checkoutOfThreeStoresThroughTheFormWithTamperedPrices() throws Exception {
        Long serumThuDuc = sp("COCOON-SERUM-30", "OS-THUDUC");
        Long tonerThuDuc = sp("INNI-TONER-200", "OS-THUDUC");
        Long serumGoVap = sp("COCOON-SERUM-30", "OS-GOVAP");
        Long foamQuan10 = sp("INNI-CLEANS-120", "OS-QUAN10");
        Long cheekThuDuc = sp("BBIA-CHEEK-08", "OS-THUDUC");
        Map<Long, Integer> stockBefore = Map.of(serumThuDuc, stock(serumThuDuc), tonerThuDuc, stock(tonerThuDuc),
                serumGoVap, stock(serumGoVap), foamQuan10, stock(foamQuan10), cheekThuDuc, stock(cheekThuDuc));
        Long a1 = add(ALICE, serumThuDuc, 2);
        Long a2 = add(ALICE, tonerThuDuc, 1);
        Long b1 = add(ALICE, serumGoVap, 1);
        Long c1 = add(ALICE, foamQuan10, 3);
        Long notChosen = add(ALICE, cheekThuDuc, 1);
        Map<String, Long> before = guard.counts();
        Map<Long, String> bobsCart = cartSnapshot(BOB);

        // what a tampered browser could add: prices, totals, owner, statuses, ids
        String tampering = "&price=1&unitPrice=1&subtotal=1&total=1&totalAmount=1&userId=6&user.id=6&cartId=1&checkoutId=1"
                + "&orderId=1&orderStatus=COMPLETED&paymentStatus=PAID&status=COMPLETED"
                + "&" + enc("groups[0].unitPrice") + "=1&" + enc("groups[0].totalAmount") + "=1&" + enc("groups[0].orderStatus")
                + "=COMPLETED&" + enc("groups[0].paymentStatus") + "=PAID&" + enc("groups[1].price") + "=1"
                + "&" + enc("items[0].unitPrice") + "=1&" + enc("items[0].quantity") + "=99&quantity=99&productName=Hack";
        HttpResponse<String> placed = postAs(ALICE, "/checkout", ids(a1, a2, b1, c1)
                + "&" + group(0, storeId("OS-GOVAP"), "STORE_PICKUP", "ONLINE", null)
                + "&" + group(1, storeId("OS-QUAN10"), "STORE_PICKUP", "PAY_AT_STORE", null)
                + "&" + group(2, storeId("OS-THUDUC"), "DELIVERY", "COD", "12 Võ Văn Ngân, P. Linh Chiểu, TP. Thủ Đức")
                + tampering);

        assertThat(placed.statusCode()).as(errorOf(placed)).isEqualTo(302);
        long checkoutId = checkoutIdOf(placed);

        // TC-06: one session, three Orders, one per Store, each with its own choices
        Map<String, Long> after = guard.counts();
        assertThat(after.get("checkout_sessions") - before.get("checkout_sessions")).isEqualTo(1);
        assertThat(after.get("orders") - before.get("orders")).isEqualTo(3);
        assertThat(after.get("order_items") - before.get("order_items")).isEqualTo(4);
        assertThat(after.get("inventory_movements") - before.get("inventory_movements")).isEqualTo(4);
        assertThat(after.get("order_status_history") - before.get("order_status_history")).isEqualTo(3);
        assertThat(after.get("payments")).as("no Payment rows in this phase").isEqualTo(before.get("payments"));

        List<Map<String, Object>> orders = ordersOf(checkoutId);
        assertThat(orders).extracting(o -> o.get("store")).containsExactly("OS-GOVAP", "OS-QUAN10", "OS-THUDUC");
        assertThat(orders).allSatisfy(o -> {
            assertThat(o.get("email")).as("owner is the logged-in customer, not the userId sent").isEqualTo(ALICE);
            assertThat(o.get("payment_status")).as("status sent by the client is ignored").isEqualTo("UNPAID");
            assertThat(o.get("pickup_code")).isNull();
            assertThat(o.get("receiver_name")).isEqualTo("Phạm Thu Hà");
        });
        Map<String, Object> goVap = orders.get(0);
        Map<String, Object> quan10 = orders.get(1);
        Map<String, Object> thuDuc = orders.get(2);
        assertThat(goVap).containsEntry("fulfillment_type", "STORE_PICKUP").containsEntry("payment_method", "ONLINE")
                .containsEntry("order_status", "PENDING_PAYMENT");
        assertThat(goVap.get("shipping_address")).isNull();
        assertThat(quan10).containsEntry("fulfillment_type", "STORE_PICKUP").containsEntry("payment_method", "PAY_AT_STORE")
                .containsEntry("order_status", "CONFIRMED");
        assertThat(thuDuc).containsEntry("fulfillment_type", "DELIVERY").containsEntry("payment_method", "COD")
                .containsEntry("order_status", "CONFIRMED");
        assertThat((String) thuDuc.get("shipping_address")).startsWith("12 Võ Văn Ngân");

        // TC-08: item snapshots carry the real StoreProduct prices and cart quantities, not what the request claimed
        List<Map<String, Object>> items = jdbc.queryForList("select s.code as store, i.store_product_id, i.product_name, "
                + "i.unit_price, i.quantity, i.subtotal, sp.store_id, o.store_id as order_store_id from dbo.order_items i "
                + "join dbo.orders o on o.order_id = i.order_id join dbo.stores s on s.store_id = o.store_id "
                + "join dbo.store_products sp on sp.store_product_id = i.store_product_id where o.checkout_id = ? "
                + "order by s.code, i.product_name", checkoutId);
        assertThat(items).extracting(i -> i.get("store") + " " + i.get("product_name") + " "
                        + ((java.math.BigDecimal) i.get("unit_price")).intValue() + " x" + i.get("quantity") + " = "
                        + ((java.math.BigDecimal) i.get("subtotal")).intValue())
                .containsExactly(
                        "OS-GOVAP Cocoon Serum Bí Đao 30ml 135000 x1 = 135000",
                        "OS-QUAN10 Innisfree Green Tea Cleansing Foam 120ml 165000 x3 = 495000",
                        "OS-THUDUC Cocoon Serum Bí Đao 30ml 129000 x2 = 258000",
                        "OS-THUDUC Innisfree Green Tea Balancing Toner 200ml 255000 x1 = 255000");
        // an Order never holds an item of another Store
        assertThat(items).allSatisfy(i -> assertThat(i.get("store_id")).isEqualTo(i.get("order_store_id")));
        assertThat(((java.math.BigDecimal) thuDuc.get("total_amount")).intValue()).isEqualTo(513000);
        assertThat(((java.math.BigDecimal) goVap.get("total_amount")).intValue()).isEqualTo(135000);
        assertThat(((java.math.BigDecimal) quan10.get("total_amount")).intValue()).isEqualTo(495000);
        assertThat(jdbc.queryForObject("select total_amount from dbo.checkout_sessions where checkout_id = ?",
                java.math.BigDecimal.class, checkoutId).intValue()).isEqualTo(1143000);
        assertThat(jdbc.queryForObject("select status from dbo.checkout_sessions where checkout_id = ?", String.class, checkoutId))
                .isEqualTo("CREATED");

        // stock went down by exactly what was ordered, with matching ORDER movements
        assertThat(stock(serumThuDuc)).isEqualTo(stockBefore.get(serumThuDuc) - 2);
        assertThat(stock(tonerThuDuc)).isEqualTo(stockBefore.get(tonerThuDuc) - 1);
        assertThat(stock(serumGoVap)).isEqualTo(stockBefore.get(serumGoVap) - 1);
        assertThat(stock(foamQuan10)).isEqualTo(stockBefore.get(foamQuan10) - 3);
        assertThat(stock(cheekThuDuc)).as("not chosen, not touched").isEqualTo(stockBefore.get(cheekThuDuc));
        List<Map<String, Object>> movements = jdbc.queryForList("select m.store_product_id, m.type, m.quantity_before, "
                + "m.quantity_change, m.quantity_after, m.staff_id from dbo.inventory_movements m join dbo.orders o "
                + "on o.order_id = m.reference_order_id where o.checkout_id = ?", checkoutId);
        assertThat(movements).hasSize(4).allSatisfy(m -> {
            assertThat(m.get("type")).isEqualTo("ORDER");
            assertThat(m.get("staff_id")).isNull();
            assertThat((Integer) m.get("quantity_change")).isNegative();
            assertThat((Integer) m.get("quantity_after")).isEqualTo((Integer) m.get("quantity_before") + (Integer) m.get("quantity_change"));
            assertThat((Integer) m.get("quantity_before")).isEqualTo(stockBefore.get(((Number) m.get("store_product_id")).longValue()));
        });

        // the cart keeps only the line that was not chosen; Bob's cart was never involved
        assertThat(cartSnapshot(ALICE)).containsExactly(Map.entry(notChosen, cheekThuDuc + "x1"));
        assertThat(cartSnapshot(BOB)).isEqualTo(bobsCart);

        // the result page shows the three Orders
        HttpResponse<String> result = get("/checkout/" + checkoutId + "?success=placed", "Cookie", login(ALICE));
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(count(result.body(), "class=\"card shadow-sm mb-3 os-order\"")).isEqualTo(3);
        assertThat(result.body()).contains("Đặt hàng thành công", "1.143.000 ₫", "513.000 ₫", "Chờ thanh toán", "Đã xác nhận",
                "Thanh toán khi nhận hàng (COD)", "Thanh toán tại cửa hàng", "Thanh toán trực tuyến",
                "Nhận tại cửa hàng OneShop Gò Vấp", "Giao hàng tận nơi");
        // another customer cannot open it
        assertThat(get("/checkout/" + checkoutId, "Cookie", login(BOB)).statusCode()).isEqualTo(404);
    }

    // ================================================================= TC-14

    @Test
    void tc14_resultPageKeepsShowingTheSnapshotAfterThePriceChanges() throws Exception {
        Long serum = sp("COCOON-SERUM-30", "OS-THUDUC");
        Long line = add(ALICE, serum, 2);
        long checkoutId = checkoutIdOf(postAs(ALICE, "/checkout",
                ids(line) + "&" + group(0, storeId("OS-THUDUC"), "STORE_PICKUP", "PAY_AT_STORE", null)));

        // the Admin raises the price afterwards
        jdbc.update("update dbo.store_products set price = 159000 where store_product_id = ?", serum);

        String later = get("/checkout/" + checkoutId, "Cookie", login(ALICE)).body();
        assertThat(later).contains("129.000 ₫", "258.000 ₫").doesNotContain("159.000 ₫", "318.000 ₫");
        assertThat(jdbc.queryForObject("select unit_price from dbo.order_items i join dbo.orders o on o.order_id = i.order_id "
                + "where o.checkout_id = ?", java.math.BigDecimal.class, checkoutId).intValue()).isEqualTo(129000);
        // while the catalog already shows the new price
        assertThat(get("/products/" + productRepository.findBySku("COCOON-SERUM-30").orElseThrow().getId(),
                "Cookie", "ONESHOP_STORE=" + storeId("OS-THUDUC")).body()).contains("159.000 ₫");
    }

    // ================================================================= refusals leave everything as it was

    @Test
    void oneLineOutOfStockRefusesTheWholeCheckoutAndShowsWhy() throws Exception {
        Long serumThuDuc = sp("COCOON-SERUM-30", "OS-THUDUC");
        Long cheekGoVap = sp("BBIA-CHEEK-08", "OS-GOVAP");
        Long a = add(ALICE, serumThuDuc, 1);
        Long b = add(ALICE, cheekGoVap, 2);
        // after the cart was filled, Gò Vấp sells out
        jdbc.update("update dbo.store_products set quantity = 0 where store_product_id = ?", cheekGoVap);
        int serumStock = stock(serumThuDuc);
        Map<String, Long> before = guard.counts();
        Map<Long, String> cart = cartSnapshot(ALICE);

        HttpResponse<String> refused = postAs(ALICE, "/checkout", ids(a, b)
                + "&" + group(0, storeId("OS-THUDUC"), "DELIVERY", "COD", "12 Võ Văn Ngân")
                + "&" + group(1, storeId("OS-GOVAP"), "STORE_PICKUP", "ONLINE", null));

        assertThat(refused.statusCode()).isEqualTo(400);
        assertThat(refused.body()).contains("BBIA Downy Cheek #08", "OneShop Gò Vấp", "hết hàng", "data-layout=\"client\"")
                .doesNotContain("Exception", "SQL", "at com.oneshop");
        // no Order for the Store that was fine, no stock taken there, cart intact
        assertThat(guard.counts()).isEqualTo(before);
        assertThat(stock(serumThuDuc)).isEqualTo(serumStock);
        assertThat(cartSnapshot(ALICE)).isEqualTo(cart);
    }

    @Test
    void invalidChoicesAreRefusedAndThePageKeepsWhatWasChosen() throws Exception {
        Long line = add(ALICE, sp("COCOON-SERUM-30", "OS-THUDUC"), 1);
        Long thuDuc = storeId("OS-THUDUC");
        Map<String, Long> before = guard.counts();

        HttpResponse<String> wrongPair = postAs(ALICE, "/checkout", ids(line) + "&" + group(0, thuDuc, "DELIVERY", "PAY_AT_STORE", "12 Võ Văn Ngân"));
        assertThat(wrongPair.statusCode()).isEqualTo(400);
        assertThat(wrongPair.body()).contains("id=\"checkout-error\"", "Đơn giao hàng chỉ thanh toán khi nhận hàng (COD) hoặc trực tuyến",
                "os-checkout-group", "12 Võ Văn Ngân");

        HttpResponse<String> noAddress = postAs(ALICE, "/checkout", ids(line) + "&" + group(0, thuDuc, "DELIVERY", "COD", null));
        assertThat(noAddress.statusCode()).isEqualTo(400);
        assertThat(noAddress.body()).contains("id=\"checkout-error\"", "Vui lòng nhập địa chỉ giao hàng");

        for (String body : List.of(
                ids(line) + "&" + group(0, thuDuc, "STORE_PICKUP", "COD", null),                 // pickup + COD
                ids(line) + "&" + group(0, thuDuc, "TELEPORT", "COD", "x"),                      // unknown fulfillment type
                ids(line) + "&" + group(0, thuDuc, "DELIVERY", "BITCOIN", "x"),                  // unknown payment method
                ids(line) + "&" + group(0, storeId("OS-GOVAP"), "DELIVERY", "COD", "x"),          // choices for another Store
                ids(line),                                                                        // no choices at all
                ids(line) + "&" + enc("groups[0].storeId") + "=" + thuDuc,                        // block without choices
                group(0, thuDuc, "DELIVERY", "COD", "x"))) {                                      // no cart lines
            HttpResponse<String> response = postAs(ALICE, "/checkout", body);
            assertThat(response.statusCode()).as(body).isEqualTo(400);
            assertThat(response.body()).as(body).doesNotContain("Exception", "at com.oneshop", "Whitelabel");
        }

        assertThat(guard.counts()).isEqualTo(before);
        assertThat(rowsOf(ALICE)).hasSize(1);
    }

    @Test
    void anotherCustomersLinesCannotBeCheckedOut() throws Exception {
        Long own = add(ALICE, sp("COCOON-SERUM-30", "OS-THUDUC"), 1);
        CartItem bobsLine = rowsOf(BOB).stream()
                .filter(line -> line.getStoreProduct().getStore().getCode().equals("OS-THUDUC")).findFirst().orElseThrow();
        Map<String, Long> before = guard.counts();
        Map<Long, String> bobsCart = cartSnapshot(BOB);

        HttpResponse<String> refused = postAs(ALICE, "/checkout", ids(own, bobsLine.getId())
                + "&" + group(0, storeId("OS-THUDUC"), "DELIVERY", "COD", "12 Võ Văn Ngân"));

        assertThat(refused.statusCode()).isEqualTo(400);
        assertThat(refused.body()).contains("id=\"cart-error\"", "không còn trong giỏ hàng của bạn");
        assertThat(guard.counts()).isEqualTo(before);
        assertThat(cartSnapshot(BOB)).isEqualTo(bobsCart);
        assertThat(rowsOf(ALICE)).hasSize(1);
    }

    @Test
    void theSameFormSubmittedAgainDoesNotOrderTwice() throws Exception {
        Long serum = sp("COCOON-SERUM-30", "OS-THUDUC");
        int stockBefore = stock(serum);
        Long line = add(ALICE, serum, 2);
        String body = ids(line) + "&" + group(0, storeId("OS-THUDUC"), "DELIVERY", "COD", "12 Võ Văn Ngân");
        Map<String, Long> before = guard.counts();

        HttpResponse<String> first = postAs(ALICE, "/checkout", body);
        HttpResponse<String> again = postAs(ALICE, "/checkout", body);

        assertThat(first.statusCode()).isEqualTo(302);
        assertThat(again.statusCode()).isEqualTo(400);
        assertThat(guard.counts().get("orders") - before.get("orders")).isEqualTo(1);
        assertThat(stock(serum)).isEqualTo(stockBefore - 2);
    }
}
