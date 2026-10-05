package com.oneshop;

import com.oneshop.dto.request.AddCartItemRequest;
import com.oneshop.dto.response.CartItemResponse;
import com.oneshop.dto.response.CartItemStatus;
import com.oneshop.dto.response.CartResponse;
import com.oneshop.dto.response.CartStoreGroupResponse;
import com.oneshop.entity.Role;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.security.jwt.JwtService;
import com.oneshop.security.service.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 7 web layer without a database (CartService is mocked): the controller always acts for the authenticated
 * customer, passes only storeProductId / cartItemId / quantity on, and the cart is closed to everyone else. The same
 * flows on real data are in {@link CartHttpDatabaseIntegrationTest}.
 */
class CartWebIntegrationTest extends AbstractIntegrationTest {

    private static final String FORM = "application/x-www-form-urlencoded";
    private static final String CUSTOMER = "customer@oneshop.test";

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    private String cookieOf(String email, RoleName role) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(email);
        user.setPasswordHash("x");
        user.setRole(new Role(role));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        return "ONESHOP_TOKEN=" + jwtService.generateToken(userDetailsService.loadUserByUsername(email));
    }

    private HttpResponse<String> postAs(String jwtCookie, String path, String body) throws Exception {
        HttpResponse<String> page = get("/login");
        Matcher field = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertThat(field.find()).isTrue();
        String csrfCookie = page.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("XSRF-TOKEN=")).map(c -> c.split(";")[0]).findFirst().orElseThrow();
        return post(path, FORM, (body.isEmpty() ? "" : body + "&") + "_csrf=" + field.group(1),
                "Cookie", (jwtCookie == null ? "" : jwtCookie + "; ") + csrfCookie);
    }

    private static CartResponse threeStoreCart() {
        CartItemResponse serum = new CartItemResponse(11L, 1L, 3L, "COCOON-SERUM-30", "Cocoon Serum Bí Đao 30ml", null,
                new BigDecimal("129000"), 2, new BigDecimal("258000"), CartItemStatus.AVAILABLE);
        CartItemResponse sameSerumElsewhere = new CartItemResponse(12L, 2L, 3L, "COCOON-SERUM-30", "Cocoon Serum Bí Đao 30ml",
                null, new BigDecimal("135000"), 1, new BigDecimal("135000"), CartItemStatus.INSUFFICIENT_STOCK);
        CartItemResponse gone = new CartItemResponse(13L, 8L, 1L, "BBIA-CHEEK-06", "BBIA Downy Cheek #06", null,
                new BigDecimal("189000"), 1, new BigDecimal("189000"), CartItemStatus.UNAVAILABLE);
        return new CartResponse(List.of(
                new CartStoreGroupResponse(2L, "OneShop Gò Vấp", "202 Quang Trung", true, List.of(sameSerumElsewhere), BigDecimal.ZERO),
                new CartStoreGroupResponse(3L, "OneShop Quận 7", "303 Nguyễn Thị Thập", false, List.of(gone), BigDecimal.ZERO),
                new CartStoreGroupResponse(1L, "OneShop Thủ Đức", "101 Võ Văn Ngân", true, List.of(serum), new BigDecimal("258000"))),
                3, new BigDecimal("258000"));
    }

    @Test
    void cartPageRendersGroupsCheckboxesAndItemStates() throws Exception {
        String jwt = cookieOf(CUSTOMER, RoleName.CUSTOMER);
        when(cartService.getCart(CUSTOMER)).thenReturn(threeStoreCart());

        String html = get("/cart", "Cookie", jwt).body();

        assertThat(html.split("os-cart-group\"", -1).length - 1).isEqualTo(3);
        assertThat(html).contains("OneShop Gò Vấp", "OneShop Quận 7", "OneShop Thủ Đức", "Chi nhánh tạm ngừng");
        // the line that can be bought has an enabled checkbox; the two others are disabled and explain why
        assertThat(checkbox(html, 11)).doesNotContain("disabled");
        assertThat(checkbox(html, 12)).contains("disabled");
        assertThat(checkbox(html, 13)).contains("disabled");
        assertThat(html).contains("Chi nhánh không còn đủ số lượng này", "Không còn bán tại chi nhánh này.");
        // the line that is no longer on sale can only be removed: no quantity form for it
        assertThat(html).contains("action=\"/cart/items/11\"", "action=\"/cart/items/12\"", "action=\"/cart/items/13/delete\"")
                .doesNotContain("action=\"/cart/items/13\"");
        assertThat(html).contains("258.000 ₫", "Tiếp tục với sản phẩm đã chọn");
    }

    private static String checkbox(String html, long cartItemId) {
        Matcher input = Pattern.compile("<input type=\"checkbox\"[^>]*name=\"cartItemIds\"[^>]*value=\"" + cartItemId + "\"[^>]*>")
                .matcher(html);
        assertThat(input.find()).as("checkbox of line " + cartItemId).isTrue();
        return input.group();
    }

    @Test
    void everyCartActionIsDoneForTheAuthenticatedCustomerOnly() throws Exception {
        String jwt = cookieOf(CUSTOMER, RoleName.CUSTOMER);

        assertThat(postAs(jwt, "/cart/items", "storeProductId=10&quantity=2&userId=99&email=other%40oneshop.test&price=1")
                .statusCode()).isEqualTo(302);
        assertThat(postAs(jwt, "/cart/items/5", "quantity=4&userId=99").statusCode()).isEqualTo(302);
        assertThat(postAs(jwt, "/cart/items/5/delete", "userId=99").statusCode()).isEqualTo(302);
        get("/cart/selection?cartItemIds=5&cartItemIds=6&userId=99", "Cookie", jwt);

        verify(cartService).addItem(CUSTOMER, new AddCartItemRequest(10L, 2));
        verify(cartService).updateItemQuantity(CUSTOMER, 5L, 4);
        verify(cartService).removeItem(CUSTOMER, 5L);
        verify(cartService).getSelection(CUSTOMER, List.of(5L, 6L));
    }

    @Test
    void refusalsOfTheServiceAreShownOnTheCartPage() throws Exception {
        String jwt = cookieOf(CUSTOMER, RoleName.CUSTOMER);
        doThrow(new BadRequestException("quantity", "Chi nhánh không đủ hàng cho số lượng bạn chọn."))
                .when(cartService).addItem(eq(CUSTOMER), any());
        doThrow(new ResourceNotFoundException("Không tìm thấy sản phẩm này trong giỏ hàng của bạn."))
                .when(cartService).removeItem(CUSTOMER, 77L);

        HttpResponse<String> refused = postAs(jwt, "/cart/items", "storeProductId=10&quantity=2");
        assertThat(refused.statusCode()).isEqualTo(400);
        assertThat(refused.body()).contains("id=\"cart-error\"", "Chi nhánh không đủ hàng cho số lượng bạn chọn.",
                "data-layout=\"client\"");

        HttpResponse<String> notMine = postAs(jwt, "/cart/items/77/delete", "");
        assertThat(notMine.statusCode()).isEqualTo(404);
        assertThat(notMine.body()).contains("Không tìm thấy sản phẩm này trong giỏ hàng của bạn.");
    }

    @Test
    void invalidInputNeverReachesTheService() throws Exception {
        String jwt = cookieOf(CUSTOMER, RoleName.CUSTOMER);

        for (String body : List.of("quantity=1", "storeProductId=10", "storeProductId=10&quantity=0", "storeProductId=10&quantity=-1")) {
            assertThat(postAs(jwt, "/cart/items", body).statusCode()).as(body).isEqualTo(400);
        }
        for (String body : List.of("quantity=0", "quantity=-4", "")) {
            assertThat(postAs(jwt, "/cart/items/5", body).statusCode()).as(body).isEqualTo(400);
        }

        verify(cartService, never()).addItem(any(), any());
        verify(cartService, never()).updateItemQuantity(any(), anyLong(), anyInt());
    }

    @Test
    void onlyCustomersHaveACart() throws Exception {
        String staff = cookieOf("staff@oneshop.test", RoleName.STAFF);
        String admin = cookieOf("admin@oneshop.test", RoleName.ADMIN);

        assertThat(get("/cart").statusCode()).isEqualTo(302);
        assertThat(postAs(null, "/cart/items", "storeProductId=10&quantity=1").statusCode()).isEqualTo(302);
        for (String role : List.of(staff, admin)) {
            assertThat(get("/cart", "Cookie", role).statusCode()).isEqualTo(403);
            assertThat(get("/cart/selection?cartItemIds=1", "Cookie", role).statusCode()).isEqualTo(403);
            assertThat(postAs(role, "/cart/items", "storeProductId=10&quantity=1").statusCode()).isEqualTo(403);
            assertThat(postAs(role, "/cart/items/5", "quantity=1").statusCode()).isEqualTo(403);
            assertThat(postAs(role, "/cart/items/5/delete", "").statusCode()).isEqualTo(403);
        }
        // a customer without a CSRF token
        String customer = cookieOf(CUSTOMER, RoleName.CUSTOMER);
        assertThat(post("/cart/items", FORM, "storeProductId=10&quantity=1", "Cookie", customer).statusCode()).isEqualTo(403);

        verify(cartService, never()).addItem(any(), any());
        verify(cartService, never()).updateItemQuantity(any(), anyLong(), anyInt());
        verify(cartService, never()).removeItem(any(), anyLong());
    }
}
