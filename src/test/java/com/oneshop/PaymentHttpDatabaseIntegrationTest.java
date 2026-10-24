package com.oneshop;

import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.CartService;
import com.oneshop.service.CheckoutService;
import com.oneshop.service.payment.PaymentReconciliationService;
import com.oneshop.service.payment.PaymentReconciliationService.SimulatedResult;
import com.oneshop.service.payment.PaymentTimeoutService;
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
import static com.oneshop.FulfillmentFixtures.STAFF_THUDUC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * V3-BE-05 end to end through HTTP on the real SQL Server: the customer payment page and the demo gateway
 * scenarios, customer cancel with simulated refund, and the Admin payment console (real Tomcat, security, CSRF,
 * SiteMesh and Thymeleaf with real data). Commits; {@link SeedGuardV3} restores the test database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.jwt.secret=test-only-secret-test-only-secret-1234567890",
        "oneshop.checkout.online-enabled=true"})
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class PaymentHttpDatabaseIntegrationTest {

    private static final String SEED_PASSWORD = "OneShop@123";
    private static final String ADMIN = "admin@oneshop.vn";
    private static final String FORM = "application/x-www-form-urlencoded";

    @LocalServerPort
    private int port;

    @Autowired
    PaymentReconciliationService reconciliation;
    @Autowired
    PaymentTimeoutService timeouts;
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
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Matcher token = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(response.body());
            assertThat(token.find()).as("login of " + email).isTrue();
            tokens.put(email, token.group(1));
        }
        return "ONESHOP_TOKEN=" + tokens.get(email);
    }

    private HttpResponse<String> getAs(String email, String path) throws IOException, InterruptedException {
        return get(path, "Cookie", login(email));
    }

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

    private String simulatePath(long order) {
        return "/orders/" + order + "/payments/" + data.attemptId(order) + "/simulate";
    }

    // ================================================================== customer payment page

    @Test
    @DisplayName("BE05-50 the payment page offers the demo scenarios with CSRF; SUCCESS confirms the Order")
    void be05_50_simulateSuccessOverHttp() throws Exception {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);

        HttpResponse<String> page = getAs(ALICE, "/orders/" + order + "/payments");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("Hạn thanh toán", "Thanh toán thành công (mô phỏng)", "name=\"scenario\"",
                "name=\"_csrf\"", simulatePath(order));

        HttpResponse<String> response = postAs(ALICE, simulatePath(order), "scenario=SUCCESS");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElse(""))
                .endsWith("/orders/" + order + "/payments?outcome=CONFIRMED");
        assertThat(data.orderStatus(order)).isEqualTo("CONFIRMED");
        HttpResponse<String> after = getAs(ALICE, "/orders/" + order + "/payments?outcome=CONFIRMED");
        assertThat(after.body()).contains("id=\"payment-outcome\"", "Đã thanh toán");
        assertThat(after.body()).doesNotContain("os-gateway-simulation");
    }

    @Test
    @DisplayName("BE05-51 forged status, amount and time in the request are ignored: only the scenario counts")
    void be05_51_forgedFieldsIgnored() throws Exception {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);

        HttpResponse<String> response = postAs(ALICE, simulatePath(order),
                "scenario=FAILED&status=SUCCESS&paymentStatus=PAID&amount=1&paidAt=2020-01-01T00:00:00"
                        + "&transactionCode=HACK&orderStatus=CONFIRMED");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.paymentStatus(order)).isEqualTo("FAILED");
        assertThat(data.successfulPayments(order)).isZero();
        assertThat(data.payments(order).get(0).get("transaction_code").toString()).doesNotContain("HACK");
    }

    @Test
    @DisplayName("BE05-52 CSRF is required (Bearer does not bypass it); other customers 404; staff and admin 403")
    void be05_52_security() throws Exception {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);
        String path = simulatePath(order);
        String bearer = "Bearer " + login(ALICE).substring("ONESHOP_TOKEN=".length());

        assertThat(postAs(ALICE, path, "scenario=SUCCESS", false).statusCode()).isEqualTo(403);
        assertThat(post(path, "scenario=SUCCESS", "Authorization", bearer).statusCode()).isEqualTo(403);
        assertThat(postAs(BOB, path, "scenario=SUCCESS").statusCode()).isEqualTo(404);
        assertThat(postAs(STAFF_THUDUC, path, "scenario=SUCCESS").statusCode()).isEqualTo(403);
        assertThat(postAs(ADMIN, path, "scenario=SUCCESS").statusCode()).isEqualTo(403);
        assertThat(postAs(ALICE, path, "scenario=BOGUS").statusCode()).isEqualTo(400);

        assertThat(data.orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(data.payments(order).get(0).get("paid_at")).isNull();
    }

    @Test
    @DisplayName("BE05-53 the old browser success / failure routes change nothing")
    void be05_53_oldRoutesRefused() throws Exception {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);
        String base = "/orders/" + order + "/payments/" + data.attemptId(order);

        assertThat(postAs(ALICE, base + "/success", "").statusCode()).isEqualTo(400);
        assertThat(postAs(ALICE, base + "/failure", "").statusCode()).isEqualTo(400);

        assertThat(data.orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(data.payments(order).get(0).get("status")).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("BE05-54 while the result is being reconciled the page says so and cancel answers 409")
    void be05_54_reconcilingPage() throws Exception {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);
        reconciliation.simulate(ALICE, order, data.attemptId(order), SimulatedResult.UNKNOWN);
        data.ageOrder(order, 20);
        timeouts.scan();

        HttpResponse<String> page = getAs(ALICE, "/orders/" + order + "/payments");
        HttpResponse<String> cancel = postAs(ALICE, "/orders/" + order + "/cancel", "");

        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("id=\"payment-reconciling\"").doesNotContain("os-gateway-simulation");
        assertThat(cancel.statusCode()).isEqualTo(409);
        assertThat(data.orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(data.cancelRestores(order)).isZero();
    }

    @Test
    @DisplayName("BE05-55 cancelling a paid Order over HTTP restores stock and shows the simulated refund")
    void be05_55_cancelPaidOverHttp() throws Exception {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
        reconciliation.simulate(ALICE, order, data.attemptId(order), SimulatedResult.SUCCESS);

        HttpResponse<String> response = postAs(ALICE, "/orders/" + order + "/cancel", "");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.paymentStatus(order)).isEqualTo("REFUNDED");
        assertThat(data.quantityAt(SERUM, "OS-THUDUC")).isEqualTo(22);
        HttpResponse<String> detail = getAs(ALICE, "/orders/" + order);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).contains("Đã hoàn tiền (mô phỏng)");
        assertThat(getAs(BOB, "/orders/" + order).statusCode()).isEqualTo(404);
    }

    // ================================================================== Admin console

    @Test
    @DisplayName("BE05-56 the Admin console: list and detail for ADMIN only; actions need CSRF; resolve through evidence")
    void be05_56_adminConsole() throws Exception {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
        reconciliation.simulate(ALICE, order, data.attemptId(order), SimulatedResult.UNKNOWN);
        data.ageOrder(order, 31);
        timeouts.scan();
        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION");

        HttpResponse<String> list = getAs(ADMIN, "/admin/payments");
        HttpResponse<String> detail = getAs(ADMIN, "/admin/payments/" + order);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains("data-order-id=\"" + order + "\"", "id=\"payment-cases-body\"");
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).contains("action-recheck", "action-gateway-collected", "IN_DOUBT",
                "RECONCILIATION_TIMEOUT");

        for (String email : new String[]{STAFF_THUDUC, ALICE}) {
            assertThat(getAs(email, "/admin/payments").statusCode()).isEqualTo(403);
            assertThat(getAs(email, "/admin/payments/" + order).statusCode()).isEqualTo(403);
            assertThat(postAs(email, "/admin/payments/" + order + "/recheck", "").statusCode()).isEqualTo(403);
        }
        assertThat(postAs(ADMIN, "/admin/payments/" + order + "/recheck", "", false).statusCode()).isEqualTo(403);
        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION");

        HttpResponse<String> recheck = postAs(ADMIN, "/admin/payments/" + order + "/recheck",
                "orderStatus=CONFIRMED&paymentStatus=PAID");
        assertThat(recheck.statusCode()).isEqualTo(302);
        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION"); // no evidence: forged fields do nothing

        HttpResponse<String> settle = postAs(ADMIN, "/admin/payments/" + order + "/gateway", "answer=NOT_COLLECTED");
        assertThat(settle.statusCode()).isEqualTo(302);
        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.cancelRestores(order)).isEqualTo(1);
        assertThat(data.quantityAt(SERUM, "OS-THUDUC")).isEqualTo(22);
        assertThat(postAs(ADMIN, "/admin/payments/" + order + "/gateway", "answer=NOT_COLLECTED").statusCode())
                .isEqualTo(409); // idempotent: the doubt is gone, nothing more to settle
        assertThat(data.cancelRestores(order)).isEqualTo(1);
    }

    @Test
    @DisplayName("BE05-57 Admin refund retry over HTTP after a failed simulation is idempotent")
    void be05_57_adminRefundRetry() throws Exception {
        long order = data.onlinePickup("OS-THUDUC", SHAMPOO, 1);
        reconciliation.simulate(ALICE, order, data.attemptId(order), SimulatedResult.SUCCESS);
        assertThat(postAs(ALICE, "/orders/" + order + "/cancel", "").statusCode()).isEqualTo(302);
        // the state a failed refund simulation leaves: collected, cancelled, money not given back yet
        jdbc.update("update dbo.payments set refund_status = 'SIMULATION_FAILED', refunded_at = null "
                + "where order_id = ?", order);
        jdbc.update("update dbo.orders set payment_status = 'PAID' where order_id = ?", order);

        HttpResponse<String> first = postAs(ADMIN, "/admin/payments/" + order + "/refund", "");
        HttpResponse<String> second = postAs(ADMIN, "/admin/payments/" + order + "/refund", "");

        assertThat(first.statusCode()).isEqualTo(302);
        assertThat(first.headers().firstValue("Location").orElse("")).contains("refund=REFUNDED");
        assertThat(second.headers().firstValue("Location").orElse("")).contains("refund=ALREADY_REFUNDED");
        assertThat(data.payments(order).get(0).get("refund_status")).isEqualTo("SIMULATED_REFUNDED");
        assertThat(data.paymentStatus(order)).isEqualTo("REFUNDED");
    }
}
