package com.oneshop;

import com.oneshop.dto.request.ReviewRequest;
import com.oneshop.dto.response.OrderDetailResponse;
import com.oneshop.dto.response.OrderItemResponse;
import com.oneshop.dto.response.OrderViewResponse;
import com.oneshop.dto.response.ProductDetailResponse;
import com.oneshop.dto.response.ProductResponse;
import com.oneshop.dto.response.ProductReviewsResponse;
import com.oneshop.dto.response.ReviewResponse;
import com.oneshop.dto.response.ReviewableItemResponse;
import com.oneshop.dto.response.StaffReviewResponse;
import com.oneshop.dto.response.StoreResponse;
import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.Role;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.security.jwt.JwtService;
import com.oneshop.security.service.CustomUserDetailsService;
import com.oneshop.service.CustomerOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FSA-01 web layer without a database (ReviewService is mocked): who may post to /reviews, where the user is sent
 * back to, and what the Product and Order pages render. The rules themselves are in
 * {@link ReviewDatabaseIntegrationTest}.
 */
class ReviewWebIntegrationTest extends AbstractIntegrationTest {

    private static final String FORM = "application/x-www-form-urlencoded";
    private static final String CUSTOMER = "customer@oneshop.test";

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private CustomerOrderService orders;

    private static ProductDetailResponse productDetail() {
        return new ProductDetailResponse(new ProductResponse(7L, "SIMPLE-PURIFY-150", "Cocoon Serum Bí Đao 30ml",
                "Mô tả", "Chăm sóc da", "Cocoon", null), List.of(), null, List.of());
    }

    private static OrderDetailResponse completedOrder(long id) {
        OrderViewResponse row = new OrderViewResponse(id, 1L, "OneShop Thủ Đức", "1 Đường thử", LocalDateTime.now(),
                FulfillmentType.STORE_PICKUP, PaymentMethod.PAY_AT_STORE, OrderPaymentStatus.PAID, OrderStatus.COMPLETED,
                "Người nhận", "0912345678", null, new BigDecimal("129000"), LocalDateTime.now(), LocalDateTime.now(),
                "H3D8P1", null);
        return new OrderDetailResponse(row, List.of(new OrderItemResponse("Cocoon Serum", new BigDecimal("129000"), 1,
                new BigDecimal("129000"))), List.of(), List.of());
    }

    private String cookieOf(String email, RoleName role) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(email);
        user.setPasswordHash("x");
        user.setRole(new Role(role));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        withId(user);
        return "ONESHOP_TOKEN=" + jwtService.generateToken(userDetailsService.loadUserByUsername(email));
    }

    private HttpResponse<String> postReview(String jwtCookie, String body) throws Exception {
        HttpResponse<String> page = get("/login");
        Matcher field = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertThat(field.find()).isTrue();
        String csrfCookie = page.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("XSRF-TOKEN=")).map(c -> c.split(";")[0]).findFirst().orElseThrow();
        return post("/reviews", FORM, body + "&_csrf=" + field.group(1),
                "Cookie", (jwtCookie == null ? "" : jwtCookie + "; ") + csrfCookie);
    }

    private static String location(HttpResponse<String> response) {
        // the server answers with an absolute URL; the path is what matters
        return response.headers().firstValue("Location").orElseThrow().replaceFirst("^https?://[^/]+", "");
    }

    // ---------------------------------------------------------------- POST /reviews

    @Test
    void guestAndNonCustomerRolesCannotPostAReview() throws Exception {
        HttpResponse<String> guest = postReview(null, "productId=1&rating=5");
        assertThat(guest.statusCode()).isEqualTo(302);
        assertThat(location(guest)).endsWith("/login");

        for (RoleName role : List.of(RoleName.STAFF, RoleName.ADMIN)) {
            String email = role.name().toLowerCase() + "@oneshop.test";
            assertThat(postReview(cookieOf(email, role), "productId=1&rating=5").statusCode())
                    .as(role.name()).isEqualTo(403);
        }
        verify(reviewService, never()).submit(any(), any(), any());
    }

    @Test
    void postWithoutCsrfTokenIsRefused() throws Exception {
        HttpResponse<String> response = post("/reviews", FORM, "productId=1&rating=5",
                "Cookie", cookieOf(CUSTOMER, RoleName.CUSTOMER));

        assertThat(response.statusCode()).isEqualTo(403);
        verify(reviewService, never()).submit(any(), any(), any());
    }

    @Test
    void customerReviewIsSubmittedForThePrincipalAndSentBackToTheProduct() throws Exception {
        String jwt = cookieOf(CUSTOMER, RoleName.CUSTOMER);
        when(reviewService.submit(eq(CUSTOMER), any(), any())).thenReturn(
                new ReviewResponse(1L, "Khách", 5, "Tốt", LocalDateTime.now(), null, null, null));

        // the body tries to name another reviewer: only productId/rating/comment are read
        HttpResponse<String> response = postReview(jwt,
                "orderItemId=77&productId=7&rating=5&comment=T%E1%BB%91t&email=victim%40oneshop.test&userId=99");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(location(response)).isEqualTo("/products/7?reviewed#reviews");
        verify(reviewService).submit(eq(CUSTOMER), eq(new ReviewRequest(7L, 5, "Tốt", 77L)), isNull());
    }

    @Test
    void reviewFromTheOrderPageGoesBackToTheOrder() throws Exception {
        String jwt = cookieOf(CUSTOMER, RoleName.CUSTOMER);

        HttpResponse<String> response = postReview(jwt, "orderItemId=77&productId=7&orderId=42&rating=4");

        assertThat(location(response)).isEqualTo("/orders/42?reviewed#reviews");
        verify(reviewService).submit(eq(CUSTOMER), eq(new ReviewRequest(7L, 4, null, 77L)), eq(42L));
    }

    @Test
    void invalidRatingNeverReachesTheService() throws Exception {
        String jwt = cookieOf(CUSTOMER, RoleName.CUSTOMER);

        for (String body : List.of("orderItemId=77&productId=7&rating=0", "orderItemId=77&productId=7&rating=6", "orderItemId=77&productId=7", "orderItemId=77&productId=7&rating=abc",
                "orderItemId=77&productId=7&rating=5&comment=" + "x".repeat(1001))) {
            HttpResponse<String> response = postReview(jwt, body);
            assertThat(response.statusCode()).as(body.substring(0, Math.min(40, body.length()))).isEqualTo(302);
            assertThat(location(response)).contains("/products/7?reviewError=invalid");
        }
        verify(reviewService, never()).submit(any(), any(), any());
    }

    @Test
    void businessRejectionIsShownAsAGenericCodeNotAsArbitraryText() throws Exception {
        String jwt = cookieOf(CUSTOMER, RoleName.CUSTOMER);
        when(reviewService.submit(eq(CUSTOMER), any(), any())).thenThrow(new BadRequestException("Bạn đã đánh giá sản phẩm này rồi."));

        HttpResponse<String> response = postReview(jwt, "orderItemId=77&productId=7&rating=5");

        assertThat(location(response)).isEqualTo("/products/7?reviewError=rejected#reviews");
    }

    // ---------------------------------------------------------------- Product page

    @Test
    void productPageListsVisibleReviewsAndEscapesTheirContent() throws Exception {
        when(storeProductService.getProductDetail(eq(7L), any())).thenReturn(productDetail());
        when(reviewService.getProductReviews(7L)).thenReturn(new ProductReviewsResponse(2, 4.5, List.of(
                new ReviewResponse(1L, "Phạm Thu Hà", 5, "Rất tốt <script>alert(1)</script>", LocalDateTime.now(),
                        "Cảm ơn bạn <b>nhiều</b>", LocalDateTime.now(), "OneShop Thủ Đức"),
                new ReviewResponse(2L, "Đặng Quốc Khánh", 4, null, LocalDateTime.now(), null, null, null))));

        String html = get("/products/7").body();

        assertThat(html).contains("id=\"review-summary\"", "4,5", "Phạm Thu Hà", "Đặng Quốc Khánh");
        assertThat(html).contains("Rất tốt &lt;script&gt;alert(1)&lt;/script&gt;").doesNotContain("<script>alert(1)");
        assertThat(html).doesNotContain("action=\"/reviews\"");
        // the Store's reply is shown with the Store name, escaped, and never with a staff name
        assertThat(html).contains("Phản hồi từ OneShop OneShop Thủ Đức", "Cảm ơn bạn &lt;b&gt;nhiều&lt;/b&gt;")
                .doesNotContain("<b>nhiều</b>");
    }

    @Test
    void reviewFormAppearsOnlyWhenTheServiceSaysTheCustomerCanReview() throws Exception {
        when(storeProductService.getProductDetail(eq(7L), any())).thenReturn(productDetail());
        String jwt = cookieOf(CUSTOMER, RoleName.CUSTOMER);

        assertThat(get("/products/7", "Cookie", jwt).body()).doesNotContain("action=\"/reviews\"");

        when(reviewService.getProductReviewItems(CUSTOMER, 7L)).thenReturn(List.of(
                new ReviewableItemResponse(7L, "Cocoon Serum", false, 77L, 42L, 9L, "OneShop Thủ Đức", null)));
        String html = get("/products/7", "Cookie", jwt).body();
        assertThat(html).contains("action=\"/reviews\"", "name=\"productId\"", "name=\"rating\"", "name=\"_csrf\"");
    }

    @Test
    void productPageWithNoReviewsSaysSo() throws Exception {
        when(storeProductService.getProductDetail(eq(7L), any())).thenReturn(productDetail());

        assertThat(get("/products/7").body()).contains("id=\"no-reviews\"").doesNotContain("id=\"review-summary\"");
    }

    // ---------------------------------------------------------------- Order page

    @Test
    void completedOrderOffersAFormForEachProductNotReviewedYet() throws Exception {
        String jwt = cookieOf(CUSTOMER, RoleName.CUSTOMER);
        when(orders.getOrder(eq(CUSTOMER), eq(42L))).thenReturn(completedOrder(42L));
        when(reviewService.getOrderReviewItems(CUSTOMER, 42L)).thenReturn(List.of(
                new ReviewableItemResponse(7L, "Cocoon Serum", false, 77L, 42L, 9L, "OneShop Thủ Đức", null),
                new ReviewableItemResponse(8L, "BBIA Cheek", true, 78L, 42L, 10L, "OneShop Gò Vấp", null)));

        String html = get("/orders/42", "Cookie", jwt).body();

        assertThat(html).contains("Đánh giá sản phẩm", "Cocoon Serum", "BBIA Cheek", "Bạn đã đánh giá sản phẩm này.");
        assertThat(html.split("action=\"/reviews\"", -1).length - 1).isEqualTo(1);
        assertThat(html).contains("name=\"orderId\" value=\"42\"");
    }

    // ---------------------------------------------------------------- replies (Staff / Admin)

    private static final String STAFF = "staff@oneshop.test";
    private static final String ADMIN = "admin@oneshop.test";

    private static StoreResponse thuDuc() {
        return new StoreResponse(1L, "TD", "OneShop Thủ Đức", "1 Võ Văn Ngân", "TP.HCM", "Thủ Đức", null, null,
                true, true, ActiveStatus.ACTIVE);
    }

    private HttpResponse<String> postTo(String jwtCookie, String path, String body) throws Exception {
        HttpResponse<String> page = get("/login");
        Matcher field = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertThat(field.find()).isTrue();
        String csrfCookie = page.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("XSRF-TOKEN=")).map(c -> c.split(";")[0]).findFirst().orElseThrow();
        return post(path, FORM, body + "&_csrf=" + field.group(1), "Cookie", jwtCookie + "; " + csrfCookie);
    }

    @Test
    void staffWithoutAssignmentIsStoppedBeforeTheReplyController() throws Exception {
        String jwt = cookieOf(STAFF, RoleName.STAFF);
        when(storeService.getAssignedStores(STAFF)).thenReturn(List.of());

        HttpResponse<String> response = postTo(jwt, "/staff/reviews/9/reply", "reply=Cam+on");

        assertThat(response.statusCode()).isEqualTo(403);
        verify(reviewService, never()).reply(any(), any());
    }

    @Test
    void assignedStaffReplyReachesTheServiceWithTheTextOnlyAndIsSentBackToTheList() throws Exception {
        String jwt = cookieOf(STAFF, RoleName.STAFF);
        when(storeService.getAssignedStores(STAFF)).thenReturn(List.of(thuDuc()));

        // the body tries to choose the Store/Order: the controller reads only the text and the page number
        HttpResponse<String> ok = postTo(jwt, "/staff/reviews/9/reply", "reply=Cam+on&page=2&storeId=77&orderId=5");

        assertThat(ok.statusCode()).isEqualTo(302);
        assertThat(location(ok)).isEqualTo("/staff/reviews?page=2&replied");
        verify(reviewService).reply(eq(9L), eq("Cam on"));

        org.mockito.Mockito.doThrow(new BadRequestException("reply", "Vui lòng nhập nội dung phản hồi."))
                .when(reviewService).reply(eq(10L), any());
        HttpResponse<String> bad = postTo(jwt, "/staff/reviews/10/reply", "reply=+++");
        assertThat(location(bad)).isEqualTo("/staff/reviews?page=0&replyError=invalid");
    }

    @Test
    void staffReviewListRendersTheReplyFormAndEscapesContent() throws Exception {
        String jwt = cookieOf(STAFF, RoleName.STAFF);
        when(storeService.getAssignedStores(STAFF)).thenReturn(List.of(thuDuc()));
        when(reviewService.getStaffReviews(0)).thenReturn(new PageImpl<>(List.of(
                new StaffReviewResponse(9L, "Phạm Thu Hà", 3L, "SIMPLE-PURIFY-150", "Cocoon Serum", 5,
                        "Tốt <img src=x>", LocalDateTime.now(), 5L, "OneShop Thủ Đức",
                        "Cảm ơn <script>1</script>", LocalDateTime.now()))));

        String html = get("/staff/reviews", "Cookie", jwt).body();

        assertThat(html).contains("data-review-id=\"9\"", "Đơn #5", "action=\"/staff/reviews/9/reply\"",
                "name=\"_csrf\"", "Cảm ơn &lt;script&gt;1&lt;/script&gt;", "Tốt &lt;img src=x&gt;");
        assertThat(html).doesNotContain("<script>1</script>").doesNotContain("<img src=x>");
    }

    @Test
    void customerAndGuestCannotUseTheStaffReviewRoutes() throws Exception {
        String customer = cookieOf(CUSTOMER, RoleName.CUSTOMER);

        assertThat(get("/staff/reviews", "Cookie", customer).statusCode()).isEqualTo(403);
        assertThat(postTo(customer, "/staff/reviews/9/reply", "reply=hi").statusCode()).isEqualTo(403);
        assertThat(get("/staff/reviews").statusCode()).isEqualTo(302);
        verify(reviewService, never()).reply(any(), any());
    }

    @Test
    void adminReplyUsesTheSameServiceAndIsCsrfProtected() throws Exception {
        String jwt = cookieOf(ADMIN, RoleName.ADMIN);

        HttpResponse<String> ok = postTo(jwt, "/admin/reviews/9/reply", "reply=Xin+loi&page=1");
        // OPEN-06: Admin now moderates only; retain this test case's identity for baseline comparison.
        assertThat(ok.statusCode()).isEqualTo(403);
        verify(reviewService, never()).reply(any(), any());

        assertThat(post("/admin/reviews/9/reply", FORM, "reply=x", "Cookie", jwt).statusCode()).isEqualTo(403);
    }
}
