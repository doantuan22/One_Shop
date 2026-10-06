package com.oneshop;

import com.oneshop.dto.request.CheckoutRequest;
import com.oneshop.dto.request.StoreGroupCheckoutRequest;
import com.oneshop.dto.response.CartItemResponse;
import com.oneshop.dto.response.CartItemStatus;
import com.oneshop.dto.response.CartResponse;
import com.oneshop.dto.response.CartStoreGroupResponse;
import com.oneshop.dto.response.CheckoutPreviewResponse;
import com.oneshop.dto.response.CheckoutResultResponse;
import com.oneshop.entity.CheckoutStatus;
import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.Role;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
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
 * Phase 8 web layer without a database (CheckoutService is mocked): what the controller binds from the form and
 * hands to the service, how refusals are shown, and who may reach it. The real flow is in
 * {@link CheckoutHttpDatabaseIntegrationTest}.
 */
class CheckoutWebIntegrationTest extends AbstractIntegrationTest {

    private static final String FORM = "application/x-www-form-urlencoded";
    private static final String CUSTOMER = "customer@oneshop.test";

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void preview() {
        CartItemResponse serum = new CartItemResponse(11L, 1L, 3L, "COCOON-SERUM-30", "Cocoon Serum Bí Đao 30ml", null,
                new BigDecimal("129000"), 2, new BigDecimal("258000"), CartItemStatus.AVAILABLE);
        CartItemResponse foam = new CartItemResponse(12L, 20L, 6L, "INNI-CLEANS-120", "Innisfree Foam", null,
                new BigDecimal("165000"), 1, new BigDecimal("165000"), CartItemStatus.AVAILABLE);
        CartResponse selection = new CartResponse(List.of(
                new CartStoreGroupResponse(1L, "OneShop Thủ Đức", "101 Võ Văn Ngân", true, true, true, List.of(serum), new BigDecimal("258000")),
                new CartStoreGroupResponse(4L, "OneShop Quận 10", "404 Ba Tháng Hai", true, false, true, List.of(foam), new BigDecimal("165000"))),
                2, new BigDecimal("423000"));
        when(checkoutService.prepare(eq(CUSTOMER), any()))
                .thenReturn(new CheckoutPreviewResponse(selection, "Khách Hàng", "0911111111", "12 Võ Văn Ngân"));
    }

    private String cookieOf(String email, RoleName role) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(email);
        user.setPasswordHash("x");
        user.setRole(new Role(role));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
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

    private static String twoGroups() {
        return "cartItemIds=11&cartItemIds=12"
                + "&" + enc("groups[0].storeId") + "=1&" + enc("groups[0].fulfillmentType") + "=DELIVERY&" + enc("groups[0].paymentMethod")
                + "=COD&" + enc("groups[0].receiverName") + "=" + enc("Phạm Thu Hà") + "&" + enc("groups[0].receiverPhone") + "=0911111111&"
                + enc("groups[0].shippingAddress") + "=" + enc("12 Võ Văn Ngân")
                + "&" + enc("groups[1].storeId") + "=4&" + enc("groups[1].fulfillmentType") + "=STORE_PICKUP&" + enc("groups[1].paymentMethod")
                + "=PAY_AT_STORE&" + enc("groups[1].receiverName") + "=" + enc("Phạm Thu Hà") + "&" + enc("groups[1].receiverPhone") + "=0911111111";
    }

    @Test
    void checkoutPageHasOneBlockPerStoreWithSuggestedReceiverAndOnlySupportedOptions() throws Exception {
        String html = get("/checkout?cartItemIds=11&cartItemIds=12", "Cookie", cookieOf(CUSTOMER, RoleName.CUSTOMER)).body();

        assertThat(html.split("os-checkout-group\"", -1).length - 1).isEqualTo(2);
        assertThat(html).contains("name=\"cartItemIds\" value=\"11\"", "name=\"cartItemIds\" value=\"12\"",
                "value=\"Khách Hàng\"", "value=\"0911111111\"", "12 Võ Văn Ngân", "423.000 ₫", "Xác nhận đặt hàng");
        // Quận 10 does not deliver: only one DELIVERY radio on the page (Thủ Đức), two pickup radios
        assertThat(html.split("value=\"DELIVERY\"", -1).length - 1).isEqualTo(1);
        assertThat(html.split("value=\"STORE_PICKUP\"", -1).length - 1).isEqualTo(2);
        verify(checkoutService).prepare(CUSTOMER, List.of(11L, 12L));
    }

    @Test
    void theFormIsBoundToOneChoicePerStoreAndPlacedForTheAuthenticatedCustomer() throws Exception {
        when(checkoutService.placeOrder(eq(CUSTOMER), any())).thenReturn(
                new CheckoutResultResponse(77L, CheckoutStatus.CREATED, new BigDecimal("423000"), LocalDateTime.now(), List.of()));

        HttpResponse<String> response = postAs(cookieOf(CUSTOMER, RoleName.CUSTOMER),
                twoGroups() + "&userId=99&totalAmount=1&" + enc("groups[0].unitPrice") + "=1");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElseThrow()).endsWith("/checkout/77?success=placed");
        ArgumentCaptor<CheckoutRequest> request = ArgumentCaptor.forClass(CheckoutRequest.class);
        verify(checkoutService).placeOrder(eq(CUSTOMER), request.capture());
        assertThat(request.getValue().cartItemIds()).containsExactly(11L, 12L);
        assertThat(request.getValue().groups()).containsExactly(
                new StoreGroupCheckoutRequest(1L, FulfillmentType.DELIVERY, PaymentMethod.COD, "Phạm Thu Hà", "0911111111", "12 Võ Văn Ngân"),
                new StoreGroupCheckoutRequest(4L, FulfillmentType.STORE_PICKUP, PaymentMethod.PAY_AT_STORE, "Phạm Thu Hà", "0911111111", null));
    }

    @Test
    void aRefusalShowsTheCheckoutPageAgainWithTheReasonAndTheChoicesMade() throws Exception {
        when(checkoutService.placeOrder(eq(CUSTOMER), any()))
                .thenThrow(new BadRequestException("cartItemIds", "\"Cocoon Serum Bí Đao 30ml\" tại OneShop Thủ Đức đã hết hàng."));

        HttpResponse<String> response = postAs(cookieOf(CUSTOMER, RoleName.CUSTOMER), twoGroups());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("id=\"checkout-error\"", "đã hết hàng", "value=\"Phạm Thu Hà\"", "os-checkout-group");
        // the pickup choice made for Quận 10 is still selected
        Matcher payAtStore = Pattern.compile("<input[^>]*id=\"store-4\"[^>]*>").matcher(response.body());
        assertThat(payAtStore.find()).isTrue();
        assertThat(payAtStore.group()).contains("checked");
        Matcher codAtThuDuc = Pattern.compile("<input[^>]*id=\"cod-1\"[^>]*>").matcher(response.body());
        assertThat(codAtThuDuc.find()).isTrue();
        assertThat(codAtThuDuc.group()).contains("checked");
    }

    @Test
    void aLockConflictIsReportedAsBusyNotAsAnError() throws Exception {
        when(checkoutService.placeOrder(eq(CUSTOMER), any())).thenThrow(new CannotAcquireLockException("deadlock victim"));

        HttpResponse<String> response = postAs(cookieOf(CUSTOMER, RoleName.CUSTOMER), twoGroups());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("id=\"checkout-error\"", "Vui lòng thử đặt hàng lại").doesNotContain("deadlock");
    }

    @Test
    void invalidFormValuesNeverReachTheService() throws Exception {
        String jwt = cookieOf(CUSTOMER, RoleName.CUSTOMER);

        for (String body : List.of(
                twoGroups().replace("DELIVERY", "TELEPORT"),
                twoGroups().replace("=COD", "=BITCOIN"),
                twoGroups().replace(enc("groups[0].receiverPhone") + "=0911111111", enc("groups[0].receiverPhone") + "=abc"),
                "cartItemIds=11")) {
            assertThat(postAs(jwt, body).statusCode()).as(body).isEqualTo(400);
        }

        verify(checkoutService, never()).placeOrder(any(), any());
    }

    @Test
    void onlyCustomersWithACsrfTokenCanCheckOut() throws Exception {
        assertThat(get("/checkout?cartItemIds=11").statusCode()).isEqualTo(302);
        assertThat(postAs(null, twoGroups()).statusCode()).isEqualTo(302);
        for (String role : List.of(cookieOf("staff@oneshop.test", RoleName.STAFF), cookieOf("admin@oneshop.test", RoleName.ADMIN))) {
            assertThat(get("/checkout?cartItemIds=11", "Cookie", role).statusCode()).isEqualTo(403);
            assertThat(get("/checkout/77", "Cookie", role).statusCode()).isEqualTo(403);
            assertThat(postAs(role, twoGroups()).statusCode()).isEqualTo(403);
        }
        assertThat(post("/checkout", FORM, twoGroups(), "Cookie", cookieOf(CUSTOMER, RoleName.CUSTOMER)).statusCode()).isEqualTo(403);

        verify(checkoutService, never()).placeOrder(any(), any());
    }
}
