package com.oneshop;

import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.CartService;
import com.oneshop.service.CheckoutService;
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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.oneshop.FulfillmentFixtures.ALICE;
import static com.oneshop.FulfillmentFixtures.BOB;
import static com.oneshop.FulfillmentFixtures.SERUM;
import static com.oneshop.FulfillmentFixtures.SHAMPOO;
import static com.oneshop.FulfillmentFixtures.STAFF_GOVAP;
import static com.oneshop.FulfillmentFixtures.STAFF_THUDUC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * V3-BE-04 end to end through HTTP on the real SQL Server: real Tomcat (security filters, CSRF, form binding,
 * SiteMesh, Thymeleaf with real data). Requests are made the way a browser makes them.
 *
 * <p>These tests commit; {@link SeedGuardV3} puts the test database back. Only against a {@code *_test} database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.jwt.secret=test-only-secret-test-only-secret-1234567890",
        "oneshop.checkout.online-enabled=true"})
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class FulfillmentHttpDatabaseIntegrationTest {

    private static final String SEED_PASSWORD = "OneShop@123";
    private static final String FORM = "application/x-www-form-urlencoded";

    @LocalServerPort
    private int port;

    @Autowired
    CartService cartService;
    @Autowired
    CheckoutService checkoutService;
    @Autowired
    CartRepository cartRepository;
    @Autowired
    CartItemRepository cartItemRepository;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    StoreRepository storeRepository;
    @Autowired
    StoreProductRepository storeProductRepository;
    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final Map<String, String> tokens = new HashMap<>();
    private SeedGuardV3 guard;
    private FulfillmentFixtures data;
    private boolean remembered;

    @BeforeEach
    void remember() {
        guard = new SeedGuardV3(jdbc);
        assumeTrue(guard.isTestDatabase(), "these tests commit: run with DB_NAME=oneshop_test");
        guard.remember();
        remembered = true;
        data = new FulfillmentFixtures(jdbc, cartService, checkoutService, cartRepository, cartItemRepository,
                productRepository, storeRepository, storeProductRepository);
    }

    @AfterEach
    void restore() {
        if (remembered) {
            guard.restore();
        }
    }

    // ------------------------------------------------------------------ HTTP helpers

    private HttpResponse<String> get(String path, String... headers) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "text/html").GET();
        if (headers.length > 0) {
            request.headers(headers);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(String path, String body, String... headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "text/html").header("Content-Type", FORM)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (headers.length > 0) {
            request.headers(headers);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String login(String email) throws IOException, InterruptedException {
        if (!tokens.containsKey(email)) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\""
                            + SEED_PASSWORD + "\"}", StandardCharsets.UTF_8)).build();
            HttpResponse<String> login = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Matcher token = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(login.body());
            assertThat(token.find()).as("login of " + email).isTrue();
            tokens.put(email, token.group(1));
        }
        return "ONESHOP_TOKEN=" + tokens.get(email);
    }

    private HttpResponse<String> getAs(String email, String path) throws IOException, InterruptedException {
        return get(path, "Cookie", login(email));
    }

    /** POST as the logged-in browser sends it: JWT cookie + CSRF field and cookie (or none when asked). */
    private HttpResponse<String> postAs(String email, String path, String body, boolean csrf)
            throws IOException, InterruptedException {
        String jwt = login(email);
        if (!csrf) {
            return post(path, body, "Cookie", jwt);
        }
        HttpResponse<String> page = get("/", "Cookie", jwt);
        Matcher field = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertThat(field.find()).isTrue();
        String csrfCookie = page.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("XSRF-TOKEN=")).map(c -> c.split(";")[0]).findFirst().orElseThrow();
        return post(path, body + (body.isEmpty() ? "" : "&") + "_csrf=" + field.group(1),
                "Cookie", jwt + "; " + csrfCookie);
    }

    private HttpResponse<String> postAs(String email, String path, String body)
            throws IOException, InterruptedException {
        return postAs(email, path, body, true);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // ================================================================== tests

    @Test
    @DisplayName("BE04-57 the queue page lists the Staff's own Fulfillments; /staff/orders is the same queue")
    void be04_57_queuePage() throws Exception {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);
        long foreign = data.onlyFulfillment(data.codDelivery("OS-GOVAP", SHAMPOO, 1));

        HttpResponse<String> queue = getAs(STAFF_THUDUC, "/staff/fulfillments");
        HttpResponse<String> alias = getAs(STAFF_THUDUC, "/staff/orders");

        assertThat(queue.statusCode()).isEqualTo(200);
        assertThat(queue.body()).contains("data-fulfillment-id=\"" + fulfillment + "\"", "id=\"fulfillments-body\"")
                .doesNotContain("data-fulfillment-id=\"" + foreign + "\"");
        assertThat(alias.statusCode()).isEqualTo(200);
        assertThat(alias.body()).contains("data-fulfillment-id=\"" + fulfillment + "\"");
        assertThat(getAs(STAFF_THUDUC, "/staff/fulfillments/delivery").statusCode()).isEqualTo(200);
        assertThat(getAs(STAFF_THUDUC, "/staff/fulfillments/pickup").statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("BE04-58 detail of another Store's Fulfillment is 403; of an unknown one 404; customers 403")
    void be04_58_detailAuthorization() throws Exception {
        long fulfillment = data.onlyFulfillment(data.codDelivery("OS-THUDUC", SERUM, 1));

        assertThat(getAs(STAFF_GOVAP, "/staff/fulfillments/" + fulfillment).statusCode()).isEqualTo(403);
        assertThat(getAs(STAFF_THUDUC, "/staff/fulfillments/999999999").statusCode()).isEqualTo(404);
        assertThat(getAs(ALICE, "/staff/fulfillments/" + fulfillment).statusCode()).isEqualTo(403);
        HttpResponse<String> own = getAs(STAFF_THUDUC, "/staff/fulfillments/" + fulfillment);
        assertThat(own.statusCode()).isEqualTo(200);
        assertThat(own.body()).contains("Phần giao #" + fulfillment, "Bắt đầu chuẩn bị",
                "data-layout=\"staff\"");
    }

    @Test
    @DisplayName("BE04-59 POST step: redirect, state changed, and injected status/store parameters are ignored")
    void be04_59_postStepIgnoresInjectedFields() throws Exception {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);

        HttpResponse<String> response = postAs(STAFF_THUDUC, "/staff/fulfillments/" + fulfillment + "/prepare",
                "orderStatus=COMPLETED&status=COMPLETED&storeId=2&fulfillmentId=1&paymentStatus=PAID");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElse("")).endsWith("/staff/fulfillments/" + fulfillment);
        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("PREPARING");
        assertThat(data.orderStatus(order)).isEqualTo("PROCESSING");
        assertThat(data.paymentStatus(order)).isEqualTo("UNPAID");
    }

    @Test
    @DisplayName("BE04-60 POST without CSRF, by another Store or by a customer changes nothing")
    void be04_60_postRefused() throws Exception {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);
        String path = "/staff/fulfillments/" + fulfillment + "/prepare";

        assertThat(postAs(STAFF_THUDUC, path, "", false).statusCode()).isEqualTo(403);
        assertThat(postAs(STAFF_GOVAP, path, "").statusCode()).isEqualTo(403);
        assertThat(postAs(ALICE, path, "").statusCode()).isEqualTo(403);

        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("CONFIRMED");
        assertThat(data.orderStatus(order)).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("BE04-61 a state conflict answers 409 and an unknown action 404")
    void be04_61_conflictStatus() throws Exception {
        long fulfillment = data.onlyFulfillment(data.codDelivery("OS-THUDUC", SERUM, 1));

        assertThat(postAs(STAFF_THUDUC, "/staff/fulfillments/" + fulfillment + "/pack", "").statusCode())
                .isEqualTo(409);
        assertThat(postAs(STAFF_THUDUC, "/staff/fulfillments/" + fulfillment + "/teleport", "").statusCode())
                .isEqualTo(404);
        assertThat(postAs(STAFF_THUDUC, "/staff/fulfillments/" + fulfillment + "/fail-delivery",
                "reason=" + enc("không đủ điều kiện")).statusCode()).isEqualTo(409);
        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("BE04-62 pickup over HTTP: wrong code 400 and nothing changes; right code completes and collects")
    void be04_62_pickupOverHttp() throws Exception {
        long order = data.payAtStorePickup("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);
        String base = "/staff/fulfillments/" + fulfillment;
        assertThat(postAs(STAFF_THUDUC, base + "/prepare", "").statusCode()).isEqualTo(302);
        assertThat(postAs(STAFF_THUDUC, base + "/ready-for-pickup", "").statusCode()).isEqualTo(302);
        String code = data.pickupCode(fulfillment);

        HttpResponse<String> wrong = postAs(STAFF_THUDUC, base + "/complete-pickup", "pickupCode=ZZZZZZZZ");
        assertThat(wrong.statusCode()).isEqualTo(400);
        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("READY_FOR_PICKUP");
        assertThat(data.successfulPayments(order)).isZero();

        // the Staff page never prints the code
        assertThat(getAs(STAFF_THUDUC, base).body()).doesNotContain(code);

        HttpResponse<String> right = postAs(STAFF_THUDUC, base + "/complete-pickup", "pickupCode=" + code);
        assertThat(right.statusCode()).isEqualTo(302);
        assertThat(data.orderStatus(order)).isEqualTo("COMPLETED");
        assertThat(data.paymentStatus(order)).isEqualTo("PAID");
    }

    @Test
    @DisplayName("BE04-67 the generic handover URL completes a delivery and a pickup by the type of the Fulfillment")
    void be04_67_handover() throws Exception {
        long deliveryOrder = data.codDelivery("OS-THUDUC", SERUM, 1);
        long delivery = data.onlyFulfillment(deliveryOrder);
        for (String step : new String[]{"prepare", "pack", "ship"}) {
            assertThat(postAs(STAFF_THUDUC, "/staff/fulfillments/" + delivery + "/" + step, "").statusCode())
                    .isEqualTo(302);
        }
        assertThat(postAs(STAFF_THUDUC, "/staff/fulfillments/" + delivery + "/handover", "").statusCode())
                .isEqualTo(302);
        assertThat(data.orderStatus(deliveryOrder)).isEqualTo("COMPLETED");

        long pickupOrder = data.payAtStorePickup("OS-THUDUC", SHAMPOO, 1);
        long pickup = data.onlyFulfillment(pickupOrder);
        assertThat(postAs(STAFF_THUDUC, "/staff/fulfillments/" + pickup + "/prepare", "").statusCode()).isEqualTo(302);
        assertThat(postAs(STAFF_THUDUC, "/staff/fulfillments/" + pickup + "/ready-for-pickup", "").statusCode())
                .isEqualTo(302);
        assertThat(postAs(STAFF_THUDUC, "/staff/fulfillments/" + pickup + "/handover", "pickupCode=ZZZZZZZZ")
                .statusCode()).isEqualTo(400);
        assertThat(data.fulfillmentStatus(pickup)).isEqualTo("READY_FOR_PICKUP");
        assertThat(postAs(STAFF_THUDUC, "/staff/fulfillments/" + pickup + "/handover",
                "pickupCode=" + data.pickupCode(pickup)).statusCode()).isEqualTo(302);
        assertThat(data.orderStatus(pickupOrder)).isEqualTo("COMPLETED");
        assertThat(postAs(STAFF_GOVAP, "/staff/fulfillments/" + delivery + "/handover", "").statusCode())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("BE04-63 the V2 order URLs only redirect and no V2 POST changes anything")
    void be04_63_legacyUrls() throws Exception {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);

        HttpResponse<String> detail = getAs(STAFF_THUDUC, "/staff/orders/" + order);
        assertThat(detail.statusCode()).isEqualTo(302);
        assertThat(detail.headers().firstValue("Location").orElse("")).contains("/staff/fulfillments?orderId=" + order);
        assertThat(getAs(STAFF_THUDUC, "/staff/orders/delivery").statusCode()).isEqualTo(302);
        assertThat(getAs(STAFF_THUDUC, "/staff/pickup").statusCode()).isEqualTo(302);

        int status = postAs(STAFF_THUDUC, "/staff/orders/" + order + "/delivery/prepare", "").statusCode();
        assertThat(status).isIn(404, 405);
        assertThat(postAs(STAFF_THUDUC, "/staff/orders/delivery/" + order + "/prepare", "").statusCode())
                .isIn(404, 405);
        assertThat(data.fulfillmentStatus(fulfillment)).isEqualTo("CONFIRMED");
        assertThat(data.orderStatus(order)).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("BE04-64 the dashboard shows Fulfillment counters of the Store")
    void be04_64_dashboard() throws Exception {
        data.codDelivery("OS-THUDUC", SERUM, 1);

        HttpResponse<String> page = getAs(STAFF_THUDUC, "/staff");

        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("data-metric=\"pending-orders\"", "data-metric=\"shipping\"",
                "data-metric=\"blocked\"", "data-metric=\"failed\"", "Phần giao cần xử lý");
    }

    @Test
    @DisplayName("BE04-65 the customer order page shows the parts per Store and the code only to the owner")
    void be04_65_customerOrderPage() throws Exception {
        long order = data.payAtStorePickup("OS-THUDUC", SERUM, 1);
        long fulfillment = data.onlyFulfillment(order);
        assertThat(postAs(STAFF_THUDUC, "/staff/fulfillments/" + fulfillment + "/prepare", "").statusCode())
                .isEqualTo(302);
        HttpResponse<String> preparing = getAs(ALICE, "/orders/" + order);
        assertThat(preparing.statusCode()).isEqualTo(200);
        assertThat(preparing.body()).contains("Phần giao theo chi nhánh", "OneShop Thủ Đức")
                .doesNotContain("os-fulfillment-pickup-code");

        assertThat(postAs(STAFF_THUDUC, "/staff/fulfillments/" + fulfillment + "/ready-for-pickup", "").statusCode())
                .isEqualTo(302);
        String code = data.pickupCode(fulfillment);

        assertThat(getAs(ALICE, "/orders/" + order).body()).contains("os-fulfillment-pickup-code", code);
        assertThat(getAs(BOB, "/orders/" + order).statusCode()).isEqualTo(404);
        assertThat(getAs(STAFF_THUDUC, "/staff/fulfillments/" + fulfillment).body()).doesNotContain(code);
    }

    @Test
    @DisplayName("BE04-66 a multi-Store order page lists each Store's part and its own status")
    void be04_66_multiStoreCustomerPage() throws Exception {
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        data.markPaid(order);
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        assertThat(postAs(STAFF_THUDUC, "/staff/fulfillments/" + thuDuc + "/prepare", "").statusCode()).isEqualTo(302);

        String html = getAs(ALICE, "/orders/" + order).body();

        assertThat(html).contains("Phần giao theo chi nhánh", "OneShop Thủ Đức", "OneShop Gò Vấp",
                "Đang xử lý", "Đang chuẩn bị", "Chờ chuẩn bị");
        assertThat(html.split("os-order-fulfillment\"", -1).length - 1).isEqualTo(2);
    }
}
