package com.oneshop;

import com.oneshop.dto.request.PlaceOrderRequest;
import com.oneshop.dto.response.AllocationPlanResponse;
import com.oneshop.dto.response.CartItemResponse;
import com.oneshop.dto.response.CartItemStatus;
import com.oneshop.dto.response.CartResponse;
import com.oneshop.dto.response.CheckoutPreviewResponse;
import com.oneshop.dto.response.CheckoutResultResponse;
import com.oneshop.dto.response.PickupStoreOption;
import com.oneshop.entity.CheckoutStatus;
import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.Role;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.CheckoutConflictException;
import com.oneshop.security.jwt.JwtService;
import com.oneshop.security.service.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V3 checkout web layer without a database (CheckoutService is mocked): what the controller binds from the form and
 * hands to the service, how refusals are shown, and who may reach it. The page has no Store group and no Store choice
 * for DELIVERY; the only Store a form can name is the pickup Store. The real flow is in
 * {@link CheckoutV3DatabaseIntegrationTest}.
 */
class CheckoutWebIntegrationTest extends AbstractIntegrationTest {

    private static final String FORM = "application/x-www-form-urlencoded";
    private static final String CUSTOMER = "customer@oneshop.test";

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    private CartResponse selection;

    @BeforeEach
    void selection() {
        CartItemResponse serum = new CartItemResponse(11L, null, 3L, "COCOON-SERUM-30", "Cocoon Serum Bí Đao 30ml", null,
                new BigDecimal("129000"), 2, new BigDecimal("258000"), CartItemStatus.AVAILABLE);
        CartItemResponse shampoo = new CartItemResponse(12L, null, 5L, "COCOON-SHAM-310", "Cocoon Dầu Gội", null,
                new BigDecimal("95000"), 1, new BigDecimal("95000"), CartItemStatus.AVAILABLE);
        selection = CartResponse.of(List.of(serum, shampoo), 2, new BigDecimal("353000"));
    }

    private CheckoutPreviewResponse preview(FulfillmentType type, PaymentMethod method, boolean deliveryAvailable,
                                            boolean onlineEnabled, boolean twoStores) {
        AllocationPlanResponse plan = null;
        int orders = 1;
        boolean split = false;
        if (deliveryAvailable) {
            List<AllocationPlanResponse.Part> parts = twoStores
                    ? List.of(new AllocationPlanResponse.Part(1L, "OneShop Thủ Đức", "101 Võ Văn Ngân",
                                    new BigDecimal("258000"), List.of(new AllocationPlanResponse.Line(3L,
                                    "Cocoon Serum Bí Đao 30ml", 2, new BigDecimal("129000"), new BigDecimal("258000")))),
                            new AllocationPlanResponse.Part(2L, "OneShop Gò Vấp", "202 Quang Trung",
                                    new BigDecimal("95000"), List.of(new AllocationPlanResponse.Line(5L,
                                    "Cocoon Dầu Gội", 1, new BigDecimal("95000"), new BigDecimal("95000")))))
                    : List.of(new AllocationPlanResponse.Part(1L, "OneShop Thủ Đức", "101 Võ Văn Ngân",
                            new BigDecimal("353000"), List.of()));
            plan = new AllocationPlanResponse("abc123def456", parts.size(), new BigDecimal("353000"), parts);
            split = method == PaymentMethod.COD && type == FulfillmentType.DELIVERY && twoStores;
            orders = split ? parts.size() : 1;
        }
        return new CheckoutPreviewResponse(selection, "Khách Hàng", "0911111111", "12 Võ Văn Ngân", type, method, null,
                onlineEnabled, deliveryAvailable,
                deliveryAvailable ? null : "Giao hàng tận nơi tạm thời chưa mở: quy tắc vùng phục vụ chưa được phê duyệt.",
                List.of(new PickupStoreOption(1L, "OneShop Thủ Đức", "101 Võ Văn Ngân")), plan,
                plan == null ? "Giao hàng tận nơi tạm thời chưa mở." : null,
                new BigDecimal("0"), orders, plan == null ? null : new BigDecimal("353000"), split, 15);
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

    private HttpResponse<String> postAs(String jwtCookie, String body) throws Exception {
        HttpResponse<String> page = get("/login");
        Matcher field = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertThat(field.find()).isTrue();
        String csrfCookie = page.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("XSRF-TOKEN=")).map(c -> c.split(";")[0]).findFirst().orElseThrow();
        return post("/checkout", FORM, body + "&_csrf=" + field.group(1),
                "Cookie", (jwtCookie == null ? "" : jwtCookie + "; ") + csrfCookie);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String delivery() {
        return "cartItemIds=11&cartItemIds=12&fulfillmentType=DELIVERY&paymentMethod=COD"
                + "&receiverName=" + enc("Phạm Thu Hà") + "&receiverPhone=0911111111"
                + "&shippingAddress=" + enc("12 Võ Văn Ngân");
    }

    // ------------------------------------------------------------------ the page

    @Test
    void checkoutPageHasNoStoreGroupsAndShowsOneOrderWithItsFulfillments() throws Exception {
        when(checkoutService.preview(eq(CUSTOMER), any(), any(), any(), any(), any()))
                .thenReturn(preview(FulfillmentType.DELIVERY, PaymentMethod.ONLINE, true, true, true));

        String html = get("/checkout?cartItemIds=11&cartItemIds=12&fulfillmentType=DELIVERY&paymentMethod=ONLINE",
                "Cookie", cookieOf(CUSTOMER, RoleName.CUSTOMER)).body();

        assertThat(html).contains("name=\"cartItemIds\" value=\"11\"", "name=\"cartItemIds\" value=\"12\"",
                "value=\"Khách Hàng\"", "value=\"0911111111\"", "12 Võ Văn Ngân", "353.000 ₫", "Xác nhận đặt hàng",
                "một đơn hàng duy nhất", "OneShop Thủ Đức", "OneShop Gò Vấp",
                "name=\"planSignature\" value=\"abc123def456\"", "name=\"expectedTotal\" value=\"353000\"");
        // no per-Store form block, no Store choice for DELIVERY
        assertThat(html).doesNotContain("os-checkout-group", "groups[0]", "name=\"storeId\"");
        // one order in the summary although two Stores supply it, and the 15 minute rule is stated
        assertThat(html).containsPattern("id=\"order-count\"[^>]*>1<");
        assertThat(html).containsPattern("id=\"fulfillment-count\"[^>]*>2<");
        assertThat(html).contains("id=\"online-deadline\"");
        assertThat(html).doesNotContain("id=\"cod-split\"");
        verify(checkoutService).preview(CUSTOMER, List.of(11L, 12L), FulfillmentType.DELIVERY, PaymentMethod.ONLINE,
                null, null);
    }

    @Test
    void codFromSeveralStoresAsksForExplicitSplitConsent() throws Exception {
        when(checkoutService.preview(eq(CUSTOMER), any(), any(), any(), any(), any()))
                .thenReturn(preview(FulfillmentType.DELIVERY, PaymentMethod.COD, true, true, true));

        String html = get("/checkout?cartItemIds=11&cartItemIds=12", "Cookie", cookieOf(CUSTOMER, RoleName.CUSTOMER)).body();

        assertThat(html).contains("id=\"cod-split\"", "name=\"splitCodConsent\"", "đơn hàng độc lập");
        assertThat(html).containsPattern("id=\"order-count\"[^>]*>2<");
    }

    @Test
    void closedFlowsAreShownAsClosedNotHidden() throws Exception {
        when(checkoutService.preview(eq(CUSTOMER), any(), any(), any(), any(), any()))
                .thenReturn(preview(FulfillmentType.DELIVERY, PaymentMethod.COD, false, false, false));

        String html = get("/checkout?cartItemIds=11", "Cookie", cookieOf(CUSTOMER, RoleName.CUSTOMER)).body();

        assertThat(html).contains("id=\"delivery-blocked\"", "chưa được phê duyệt", "id=\"online-disabled\"",
                "id=\"plan-problem\"");
        // DELIVERY and ONLINE radios exist but cannot be chosen
        Matcher deliveryRadio = Pattern.compile("<input[^>]*value=\"DELIVERY\"[^>]*>").matcher(html);
        assertThat(deliveryRadio.find()).isTrue();
        assertThat(deliveryRadio.group()).contains("disabled");
        Matcher onlineRadio = Pattern.compile("<input[^>]*value=\"ONLINE\"[^>]*>").matcher(html);
        assertThat(onlineRadio.find()).isTrue();
        assertThat(onlineRadio.group()).contains("disabled");
    }

    // ------------------------------------------------------------------ the form

    @Test
    void theFormIsBoundToOneChoiceForTheWholeOrderAndPlacedForTheAuthenticatedCustomer() throws Exception {
        when(checkoutService.placeOrder(eq(CUSTOMER), any(PlaceOrderRequest.class))).thenReturn(
                new CheckoutResultResponse(77L, CheckoutStatus.CREATED, new BigDecimal("353000"), LocalDateTime.now(),
                        List.of()));

        // a browser trying to smuggle a user, a price, a total, a Store and a payment result
        HttpResponse<String> response = postAs(cookieOf(CUSTOMER, RoleName.CUSTOMER),
                delivery() + "&planSignature=abc123def456&expectedTotal=353000&splitCodConsent=true"
                        + "&userId=99&totalAmount=1&unitPrice=1&storeId=4&storeProductId=9&paymentStatus=PAID&orderStatus=CONFIRMED");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElseThrow()).endsWith("/checkout/77?success=placed");
        ArgumentCaptor<PlaceOrderRequest> request = ArgumentCaptor.forClass(PlaceOrderRequest.class);
        verify(checkoutService).placeOrder(eq(CUSTOMER), request.capture());
        assertThat(request.getValue()).isEqualTo(new PlaceOrderRequest(List.of(11L, 12L), FulfillmentType.DELIVERY,
                PaymentMethod.COD, "Phạm Thu Hà", "0911111111", "12 Võ Văn Ngân", null, true, "abc123def456",
                new BigDecimal("353000")));
    }

    @Test
    void aPickupFormNamesTheStoreItWantsToCollectAt() throws Exception {
        when(checkoutService.placeOrder(eq(CUSTOMER), any(PlaceOrderRequest.class))).thenReturn(
                new CheckoutResultResponse(78L, CheckoutStatus.CREATED, new BigDecimal("353000"), LocalDateTime.now(),
                        List.of()));

        HttpResponse<String> response = postAs(cookieOf(CUSTOMER, RoleName.CUSTOMER),
                "cartItemIds=11&fulfillmentType=STORE_PICKUP&paymentMethod=PAY_AT_STORE&pickupStoreId=1"
                        + "&receiverName=" + enc("Phạm Thu Hà") + "&receiverPhone=0911111111");

        assertThat(response.statusCode()).isEqualTo(302);
        ArgumentCaptor<PlaceOrderRequest> request = ArgumentCaptor.forClass(PlaceOrderRequest.class);
        verify(checkoutService).placeOrder(eq(CUSTOMER), request.capture());
        assertThat(request.getValue().pickupStoreId()).isEqualTo(1L);
        assertThat(request.getValue().fulfillmentType()).isEqualTo(FulfillmentType.STORE_PICKUP);
    }

    @Test
    void aRefusalShowsTheCheckoutPageAgainWithTheReasonAndTheChoicesMade() throws Exception {
        when(checkoutService.preview(eq(CUSTOMER), any(), any(), any(), any(), any()))
                .thenReturn(preview(FulfillmentType.DELIVERY, PaymentMethod.COD, true, true, true));
        when(checkoutService.placeOrder(eq(CUSTOMER), any(PlaceOrderRequest.class)))
                .thenThrow(new CheckoutConflictException(CheckoutConflictException.CODE_PLAN_CHANGED,
                        "Nguồn hàng của đơn đã thay đổi so với lúc bạn xem."));

        HttpResponse<String> response = postAs(cookieOf(CUSTOMER, RoleName.CUSTOMER), delivery());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("id=\"checkout-error\"", "data-error-code=\"PLAN_CHANGED\"",
                "đã thay đổi", "value=\"Phạm Thu Hà\"");
        // the page was rebuilt from a fresh preview, so it carries the new signature to confirm
        assertThat(response.body()).contains("name=\"planSignature\" value=\"abc123def456\"");
    }

    @Test
    void aBusinessRefusalWithoutAPreviewFallsBackToTheCart() throws Exception {
        when(checkoutService.placeOrder(eq(CUSTOMER), any(PlaceOrderRequest.class)))
                .thenThrow(new BadRequestException("cartItemIds", "Có sản phẩm đã chọn không còn trong giỏ hàng."));
        when(checkoutService.preview(eq(CUSTOMER), any(), any(), any(), any(), any()))
                .thenThrow(new BadRequestException("cartItemIds", "Có sản phẩm đã chọn không còn trong giỏ hàng."));

        HttpResponse<String> response = postAs(cookieOf(CUSTOMER, RoleName.CUSTOMER), delivery());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("không còn trong giỏ hàng");
    }

    @Test
    void aLockConflictIsReportedAsBusyNotAsAnError() throws Exception {
        when(checkoutService.preview(eq(CUSTOMER), any(), any(), any(), any(), any()))
                .thenReturn(preview(FulfillmentType.DELIVERY, PaymentMethod.COD, true, true, false));
        when(checkoutService.placeOrder(eq(CUSTOMER), any(PlaceOrderRequest.class)))
                .thenThrow(new CannotAcquireLockException("deadlock victim"));

        HttpResponse<String> response = postAs(cookieOf(CUSTOMER, RoleName.CUSTOMER), delivery());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("id=\"checkout-error\"", "Vui lòng thử đặt hàng lại")
                .doesNotContain("deadlock");
    }

    @Test
    void invalidFormValuesNeverReachTheService() throws Exception {
        when(checkoutService.preview(eq(CUSTOMER), any(), any(), any(), any(), any()))
                .thenReturn(preview(FulfillmentType.DELIVERY, PaymentMethod.COD, true, true, false));
        String jwt = cookieOf(CUSTOMER, RoleName.CUSTOMER);

        for (String body : List.of(
                delivery().replace("DELIVERY", "TELEPORT"),
                delivery().replace("=COD", "=BITCOIN"),
                delivery().replace("receiverPhone=0911111111", "receiverPhone=abc"),
                "fulfillmentType=DELIVERY&paymentMethod=COD&receiverName=A&receiverPhone=0911111111")) {
            assertThat(postAs(jwt, body).statusCode()).as(body).isEqualTo(400);
        }

        verify(checkoutService, never()).placeOrder(any(), any(PlaceOrderRequest.class));
    }

    @Test
    void onlyCustomersWithACsrfTokenCanCheckOut() throws Exception {
        assertThat(get("/checkout?cartItemIds=11").statusCode()).isEqualTo(302);
        assertThat(postAs(null, delivery()).statusCode()).isEqualTo(302);
        for (String role : List.of(cookieOf("staff@oneshop.test", RoleName.STAFF), cookieOf("admin@oneshop.test", RoleName.ADMIN))) {
            assertThat(get("/checkout?cartItemIds=11", "Cookie", role).statusCode()).isEqualTo(403);
            assertThat(get("/checkout/77", "Cookie", role).statusCode()).isEqualTo(403);
            assertThat(postAs(role, delivery()).statusCode()).isEqualTo(403);
        }
        assertThat(post("/checkout", FORM, delivery(), "Cookie", cookieOf(CUSTOMER, RoleName.CUSTOMER)).statusCode()).isEqualTo(403);

        verify(checkoutService, never()).placeOrder(any(), any(PlaceOrderRequest.class));
    }
}
