package com.oneshop;

import com.oneshop.entity.VisibilityStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static com.oneshop.FulfillmentFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Production HTTP -> security/JWT/CSRF/SiteMesh/Thymeleaf -> real services -> SQL Server (no mocks). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.jwt.secret=test-only-secret-test-only-secret-1234567890",
        "oneshop.checkout.online-enabled=true", "oneshop.payment.timeout-worker-enabled=false"})
class ReviewV3HttpDatabaseIntegrationTest extends ReviewV3TestSupport {
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final Map<String, String> tokens = new HashMap<>();

    private HttpResponse<String> send(String method, String path, String body, String... headers) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Accept", "text/html");
        if (headers.length > 0) builder.headers(headers);
        if ("GET".equals(method)) builder.GET();
        else builder.header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String login(String email) throws Exception {
        if (!tokens.containsKey(email)) {
            var req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\"OneShop@123\"}"))
                    .build();
            var response = client.send(req, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            var matcher = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(response.body());
            assertThat(matcher.find()).isTrue();
            tokens.put(email, matcher.group(1));
        }
        return "ONESHOP_TOKEN=" + tokens.get(email);
    }

    private HttpResponse<String> get(String email, String path) throws Exception {
        return send("GET", path, "", "Cookie", login(email));
    }

    private HttpResponse<String> post(String email, String path, String body, boolean csrf) throws Exception {
        String cookie = login(email);
        if (!csrf) return send("POST", path, body, "Cookie", cookie);
        var page = send("GET", "/", "", "Cookie", cookie);
        var field = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertThat(field.find()).isTrue();
        String csrfCookie = page.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN="))
                .map(c -> c.split(";")[0]).findFirst().orElseThrow();
        return send("POST", path, body + "&_csrf=" + field.group(1), "Cookie", cookie + "; " + csrfCookie);
    }

    private String location(HttpResponse<String> response) { return response.headers().firstValue("Location").orElse(""); }
    private String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    @Test void partialOrderAndProductPagesOfferOnlyReceivedItemForms() throws Exception {
        long order = partiallyReceived(true), a = item(order, "OS-THUDUC"), b = item(order, "OS-GOVAP");
        var orderPage = get(ALICE, "/orders/" + order);
        assertThat(orderPage.statusCode()).isEqualTo(200);
        assertThat(orderPage.body()).contains("data-review-item=\"" + a + "\"", "name=\"orderItemId\"", "name=\"_csrf\"")
                .doesNotContain("data-review-item=\"" + b + "\"");
        var productPage = get(ALICE, "/products/" + data.productId(SERUM));
        assertThat(productPage.statusCode()).isEqualTo(200);
        assertThat(productPage.body()).contains("data-review-item=\"" + a + "\"").doesNotContain("data-review-item=\"" + b + "\"");
    }

    @Test void submissionDerivesOwnerAndSourceFromItemAndEscapesProductOutput() throws Exception {
        long order = partiallyReceived(false), line = item(order, "OS-THUDUC");
        var response = post(ALICE, "/reviews", "orderItemId=" + line + "&orderId=" + order + "&rating=5&comment="
                + enc("Tốt <script>alert(1)</script>") + "&userId=" + userId(BOB) + "&storeId=2", true);
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(location(response)).endsWith("/orders/" + order + "?reviewed#reviews");
        var row = jdbc.queryForMap("select user_id,product_id from dbo.reviews where order_item_id=?", line);
        assertThat(row.get("user_id")).isEqualTo(userId(ALICE));
        assertThat(row.get("product_id")).isEqualTo(data.productId(SERUM));
        assertThat(get(ALICE, "/products/" + data.productId(SERUM)).body())
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;").doesNotContain("<script>alert(1)</script>");
    }

    @Test void customerIdorAndInvalidOrderContextReturn404() throws Exception {
        long order = partiallyReceived(false), line = item(order, "OS-THUDUC");
        assertThat(post(BOB, "/reviews", "orderItemId=" + line + "&rating=5", true).statusCode()).isEqualTo(404);
        assertThat(post(ALICE, "/reviews", "orderItemId=" + line + "&orderId=" + (order + 9999) + "&rating=5", true).statusCode()).isEqualTo(404);
        assertThat(get(BOB, "/orders/" + order).statusCode()).isEqualTo(404);
        assertThat(count("select count(*) from dbo.reviews where order_item_id=?", line)).isZero();
    }

    @Test void ratingLengthAndMissingItemAreRejectedBeforeWriting() throws Exception {
        long order = partiallyReceived(false), line = item(order, "OS-THUDUC");
        for (String body : new String[]{"orderItemId=" + line + "&rating=6", "orderItemId=" + line + "&rating=no",
                "orderItemId=" + line + "&rating=5&comment=" + "x".repeat(1001), "productId=3&rating=5"}) {
            var response = post(ALICE, "/reviews", body, true);
            assertThat(response.statusCode()).isEqualTo(302);
            assertThat(location(response)).contains("reviewError=invalid");
        }
        assertThat(count("select count(*) from dbo.reviews where order_item_id=?", line)).isZero();
    }

    @Test void cookieAndBearerReviewFormsBothRequireCsrf() throws Exception {
        long order = partiallyReceived(false), line = item(order, "OS-THUDUC");
        String body = "orderItemId=" + line + "&rating=5";
        assertThat(post(ALICE, "/reviews", body, false).statusCode()).isEqualTo(403);
        login(ALICE);
        assertThat(send("POST", "/reviews", body, "Authorization", "Bearer " + tokens.get(ALICE)).statusCode()).isEqualTo(403);
        assertThat(post(STAFF_THUDUC, "/reviews", body, true).statusCode()).isEqualTo(403);
    }

    @Test void staffReplyAndDetailUseActualSourceAndCrossStoreGets403() throws Exception {
        long order = partiallyReceived(false), id = submit(item(order, "OS-THUDUC")).id();
        assertThat(post(STAFF_GOVAP, "/staff/reviews/" + id + "/reply", "reply=No&storeId=1", true).statusCode()).isEqualTo(403);
        assertThat(get(STAFF_GOVAP, "/staff/reviews/" + id).statusCode()).isEqualTo(403);
        var detail = get(STAFF_THUDUC, "/staff/reviews/" + id);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).contains("name=\"_csrf\"", "OneShop Thủ Đức");
        assertThat(post(STAFF_THUDUC, "/staff/reviews/" + id + "/reply", "reply=" + enc("Cảm ơn <b>bạn</b>")
                + "&rating=1&comment=forged&storeId=2", true).statusCode()).isEqualTo(302);
        assertThat(get(ALICE, "/products/" + data.productId(SERUM)).body()).contains("Cảm ơn &lt;b&gt;bạn&lt;/b&gt;");
        assertThat(jdbc.queryForObject("select rating from dbo.reviews where review_id=?", Integer.class, id)).isEqualTo(5);
        assertThat(post(STAFF_THUDUC, "/staff/reviews/" + id + "/reply", "reply=No", false).statusCode()).isEqualTo(403);
    }

    @Test void revokedAssignmentIsEnforcedWithThePreviouslyIssuedJwt() throws Exception {
        long order = partiallyReceived(false), id = submit(item(order, "OS-THUDUC")).id();
        assertThat(get(STAFF_THUDUC, "/staff/reviews/" + id).statusCode()).isEqualTo(200);
        jdbc.update("update dbo.staff_store_assignments set status='INACTIVE' where user_id=?", userId(STAFF_THUDUC));
        assertThat(get(STAFF_THUDUC, "/staff/reviews/" + id).statusCode()).isEqualTo(403);
        assertThat(post(STAFF_THUDUC, "/staff/reviews/" + id + "/reply", "reply=No", true).statusCode()).isEqualTo(403);
    }

    @Test void adminModeratesButCannotReplyAndHiddenReviewRetainsItsReply() throws Exception {
        long order = partiallyReceived(false), id = submit(item(order, "OS-THUDUC")).id();
        reply(STAFF_THUDUC, id, "Đã lưu");
        var page = get(ADMIN, "/admin/reviews");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("data-review-id=\"" + id + "\"", "Đã lưu")
                .doesNotContain("action=\"/admin/reviews/" + id + "/reply\"");
        assertThat(post(ADMIN, "/admin/reviews/" + id + "/reply", "reply=No", true).statusCode()).isEqualTo(403);
        assertThat(post(ADMIN, "/admin/reviews/" + id + "/status", "status=HIDDEN", false).statusCode()).isEqualTo(403);
        assertThat(post(ADMIN, "/admin/reviews/" + id + "/status", "status=HIDDEN", true).statusCode()).isEqualTo(302);
        assertThat(get(STAFF_THUDUC, "/staff/reviews/" + id).statusCode()).isEqualTo(403);
        assertThat(reviews.getProductReviews(data.productId(SERUM)).latest()).noneMatch(r -> r.id().equals(id));
        assertThat(count("select count(*) from dbo.review_replies where review_id=?", id)).isEqualTo(1);
        assertThat(post(ALICE, "/admin/reviews/" + id + "/status", "status=ACTIVE", true).statusCode()).isEqualTo(403);
    }

    @Test void historicalReviewWithoutItemRendersSafelyAndNeverGrantsStaffAuthority() throws Exception {
        jdbc.update("insert into dbo.reviews(user_id,product_id,rating,comment) values(?,?,5,N'BE06 lịch sử chưa xác minh')",
                userId(BOB), data.productId(SERUM));
        long id = jdbc.queryForObject("select max(review_id) from dbo.reviews", Long.class);
        assertThat(get(ADMIN, "/admin/reviews").body()).contains("BE06 lịch sử chưa xác minh", "Chưa xác minh nguồn chi nhánh");
        assertThat(get(ALICE, "/products/" + data.productId(SERUM)).body()).contains("BE06 lịch sử chưa xác minh");
        assertThat(get(STAFF_THUDUC, "/staff/reviews").body()).doesNotContain("BE06 lịch sử chưa xác minh");
        assertThat(post(STAFF_THUDUC, "/staff/reviews/" + id + "/reply", "reply=No", true).statusCode()).isEqualTo(403);
    }
}
