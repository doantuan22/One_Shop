package com.oneshop;

import com.oneshop.dto.request.AddCartItemRequest;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.CartService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * V3 checkout end to end through HTTP on the real SQL Server: real Tomcat (security filters, CSRF, form binding,
 * SiteMesh, Thymeleaf with real data), requests made the way a browser makes them.
 *
 * <p>These tests commit; {@link SeedGuardV3} puts the test database back. Only against a {@code *_test} database.
 * The {@code oneshop.checkout.*} properties are TEST-ONLY switches (see {@link CheckoutV3DatabaseIntegrationTest}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.jwt.secret=test-only-secret-test-only-secret-1234567890",
        "oneshop.checkout.online-enabled=true"})
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class CheckoutV3HttpDatabaseIntegrationTest {

    private static final String SEED_PASSWORD = "OneShop@123";
    private static final String ALICE = "khachhang1@example.com";
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
    private ProductRepository productRepository;
    @Autowired
    private StoreRepository storeRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final Map<String, String> tokens = new HashMap<>();
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

    private HttpResponse<String> getAs(String email, String path) throws IOException, InterruptedException {
        return get(path, "Cookie", login(email));
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

    private List<Long> cart(String email, Object... skuAndQuantity) {
        cartRepository.findByUserEmail(email).ifPresent(c -> cartItemRepository.deleteAll(cartItemRepository.findByCartId(c.getId())));
        for (int i = 0; i < skuAndQuantity.length; i += 2) {
            cartService.addItem(email, AddCartItemRequest.forProduct(
                    productRepository.findBySku((String) skuAndQuantity[i]).orElseThrow().getId(), (Integer) skuAndQuantity[i + 1]));
        }
        return cartRepository.findByUserEmail(email).map(c -> cartItemRepository.findByCartIdOrderByIdAsc(c.getId()))
                .orElseThrow().stream().map(line -> line.getId()).toList();
    }

    private static String ids(List<Long> ids) {
        StringBuilder out = new StringBuilder();
        ids.forEach(id -> out.append(out.length() == 0 ? "" : "&").append("cartItemIds=").append(id));
        return out.toString();
    }

    private static Long locationId(HttpResponse<String> response, String prefix) {
        Matcher matcher = Pattern.compile(prefix + "/(\\d+)").matcher(response.headers().firstValue("Location").orElse(""));
        assertThat(matcher.find()).as("redirect to " + prefix + ": " + response.headers().firstValue("Location")).isTrue();
        return Long.valueOf(matcher.group(1));
    }

    // ------------------------------------------------------------------ the flows

    @Test
    @DisplayName("The page shows an ONE-Order plan from two Stores; confirming creates one Order and shows its parts")
    void onlineMultiStoreThroughTheBrowser() throws Exception {
        List<Long> lines = cart(ALICE, "COCOON-SERUM-30", 1, "COCOON-SHAM-310", 1);

        String page = getAs(ALICE, "/checkout?" + ids(lines) + "&fulfillmentType=DELIVERY&paymentMethod=ONLINE"
                + "&shippingAddress=" + enc("12 Đường Thử Nghiệm")).body();
        assertThat(page).contains("một đơn hàng duy nhất", "OneShop Thủ Đức", "OneShop Gò Vấp",
                "name=\"planSignature\"", "name=\"expectedTotal\"", "id=\"online-deadline\"");
        assertThat(page).containsPattern("id=\"order-count\"[^>]*>1<");
        // two Stores, one Order: one 30.000 fee and the note saying so
        assertThat(page).contains("id=\"split-note\"", "id=\"shipping-fee\"", "30.000");
        assertThat(page).doesNotContain("os-checkout-group", "groups[0]");
        Matcher signature = Pattern.compile("name=\"planSignature\" value=\"([^\"]+)\"").matcher(page);
        Matcher total = Pattern.compile("name=\"expectedTotal\" value=\"([^\"]+)\"").matcher(page);
        assertThat(signature.find()).isTrue();
        assertThat(total.find()).isTrue();

        HttpResponse<String> placed = postAs(ALICE, "/checkout", ids(lines) + "&fulfillmentType=DELIVERY&paymentMethod=ONLINE"
                + "&receiverName=" + enc("Phạm Thu Hà") + "&receiverPhone=0911111111&shippingAddress=" + enc("12 Đường Thử Nghiệm")
                + "&planSignature=" + signature.group(1) + "&expectedTotal=" + total.group(1)
                // things a browser must not be able to decide
                + "&userId=99&totalAmount=1&unitPrice=1&storeId=4&paymentStatus=PAID&orderStatus=CONFIRMED"
                + "&shippingFee=1&shipping_fee=1&shippingFeePerOrder=1");
        assertThat(placed.statusCode()).isEqualTo(302);
        Long checkoutId = locationId(placed, "/checkout");

        // one Order, two Fulfillments, pending payment, fresh deadline
        assertThat(jdbc.queryForObject("select count(*) from dbo.orders where checkout_id = ?", Long.class, checkoutId)).isEqualTo(1);
        Long orderId = jdbc.queryForObject("select order_id from dbo.orders where checkout_id = ?", Long.class, checkoutId);
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_fulfillments where order_id = ?", Long.class, orderId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select order_status from dbo.orders where order_id = ?", String.class, orderId))
                .isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("select payment_status from dbo.orders where order_id = ?", String.class, orderId))
                .isEqualTo("UNPAID");
        // a browser cannot decide the fee: one fee of 30.000 for the whole Order, whatever it sent
        assertThat(jdbc.queryForObject("select shipping_fee from dbo.orders where order_id = ?", java.math.BigDecimal.class,
                orderId)).isEqualByComparingTo("30000");

        String result = getAs(ALICE, "/checkout/" + checkoutId + "?success=placed").body();
        assertThat(result).contains("một đơn hàng", "os-fulfillments", "OneShop Thủ Đức", "OneShop Gò Vấp",
                "os-payment-deadline", "Phần giao hàng");

        // the order pages work for an Order that has no single Store
        HttpResponse<String> detail = getAs(ALICE, "/orders/" + orderId);
        assertThat(detail.statusCode()).isEqualTo(200);
        HttpResponse<String> payments = getAs(ALICE, "/orders/" + orderId + "/payments");
        assertThat(payments.statusCode()).isEqualTo(200);
        assertThat(payments.body()).contains("payment-deadline", "Nhiều chi nhánh (2)")
                .doesNotContain("Mô phỏng thành công", "Mô phỏng thất bại");

        // the old browser buttons cannot confirm a payment any more
        Long paymentId = jdbc.queryForObject("select payment_id from dbo.payments where order_id = ?", Long.class, orderId);
        HttpResponse<String> success = postAs(ALICE, "/orders/" + orderId + "/payments/" + paymentId + "/success", "x=1");
        assertThat(success.statusCode()).isGreaterThanOrEqualTo(400);
        assertThat(jdbc.queryForObject("select status from dbo.payments where payment_id = ?", String.class, paymentId))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select order_status from dbo.orders where order_id = ?", String.class, orderId))
                .isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("COD from two Stores asks for consent through the browser; with it two independent Orders are created")
    void codSplitThroughTheBrowser() throws Exception {
        List<Long> lines = cart(ALICE, "COCOON-SERUM-30", 1, "COCOON-SHAM-310", 1);
        String common = ids(lines) + "&fulfillmentType=DELIVERY&paymentMethod=COD&receiverName=" + enc("Phạm Thu Hà")
                + "&receiverPhone=0911111111&shippingAddress=" + enc("12 Đường Thử Nghiệm");

        // without consent nothing is created and the page says what is needed
        long ordersBefore = jdbc.queryForObject("select count(*) from dbo.orders", Long.class);
        HttpResponse<String> refused = postAs(ALICE, "/checkout", common);
        assertThat(refused.statusCode()).isEqualTo(400);
        assertThat(refused.body()).contains("COD_SPLIT_CONSENT_REQUIRED", "id=\"cod-split\"", "splitCodConsent",
                "os-order-split-amounts", "Phí giao hàng đơn này", "Thanh toán khi nhận đơn này", "2 đơn");
        assertThat(jdbc.queryForObject("select count(*) from dbo.orders", Long.class)).isEqualTo(ordersBefore);

        Matcher signature = Pattern.compile("name=\"planSignature\" value=\"([^\"]+)\"").matcher(refused.body());
        assertThat(signature.find()).isTrue();
        HttpResponse<String> placed = postAs(ALICE, "/checkout", common + "&splitCodConsent=true&planSignature=" + signature.group(1));
        assertThat(placed.statusCode()).isEqualTo(302);
        Long checkoutId = locationId(placed, "/checkout");

        assertThat(jdbc.queryForObject("select count(*) from dbo.orders where checkout_id = ?", Long.class, checkoutId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_fulfillments f join dbo.orders o "
                + "on o.order_id = f.order_id where o.checkout_id = ?", Long.class, checkoutId)).isEqualTo(2);
        String result = getAs(ALICE, "/checkout/" + checkoutId + "?success=placed").body();
        assertThat(result).contains("id=\"checkout-note-split\"", "độc lập");
    }

    @Test
    @DisplayName("Pickup through the browser: only Stores holding the whole cart are offered")
    void pickupThroughTheBrowser() throws Exception {
        List<Long> lines = cart(ALICE, "COCOON-SERUM-30", 1, "COCOON-SHAM-310", 1);
        // no single Store holds both SKUs
        String none = getAs(ALICE, "/checkout?" + ids(lines) + "&fulfillmentType=STORE_PICKUP").body();
        assertThat(none).contains("Chưa có chi nhánh nào đủ toàn bộ sản phẩm");

        List<Long> single = cart(ALICE, "COCOON-SERUM-30", 1, "INNI-TONER-200", 1);
        Long thuDuc = storeRepository.findByCode("OS-THUDUC").orElseThrow().getId();
        String page = getAs(ALICE, "/checkout?" + ids(single) + "&fulfillmentType=STORE_PICKUP&pickupStoreId=" + thuDuc).body();
        assertThat(page).contains("id=\"pickup-stores\"", "OneShop Thủ Đức");

        HttpResponse<String> placed = postAs(ALICE, "/checkout", ids(single) + "&fulfillmentType=STORE_PICKUP"
                + "&paymentMethod=PAY_AT_STORE&pickupStoreId=" + thuDuc + "&receiverName=" + enc("Phạm Thu Hà")
                + "&receiverPhone=0911111111");
        assertThat(placed.statusCode()).isEqualTo(302);
        Long checkoutId = locationId(placed, "/checkout");
        assertThat(jdbc.queryForObject("select count(*) from dbo.orders where checkout_id = ? and fulfillment_type = "
                + "'STORE_PICKUP' and store_id = ?", Long.class, checkoutId, thuDuc)).isEqualTo(1);
    }
}
