package com.oneshop;

import com.oneshop.dto.response.CatalogItemResponse;
import com.oneshop.dto.response.ProductDetailResponse;
import com.oneshop.dto.response.ProductResponse;
import com.oneshop.dto.response.StoreAvailabilityResponse;
import com.oneshop.dto.response.StoreResponse;
import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.Role;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.security.jwt.JwtService;
import com.oneshop.security.service.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 6 web layer without a database (services are mocked): how the selected Store cookie drives the controllers,
 * and that the Admin catalog pages are closed by the backend. The same flows on real data are in
 * {@link CatalogHttpDatabaseIntegrationTest}.
 */
class CatalogWebIntegrationTest extends AbstractIntegrationTest {

    private static final String STORE_COOKIE = "ONESHOP_STORE";
    private static final String FORM = "application/x-www-form-urlencoded";

    private static final StoreResponse GO_VAP = new StoreResponse(2L, "OS-GOVAP", "OneShop Gò Vấp", "202 Quang Trung",
            "TP. Hồ Chí Minh", "Gò Vấp", "02811110002", "08:00 - 22:00", true, true, ActiveStatus.ACTIVE);
    private static final ProductResponse SERUM = new ProductResponse(3L, "COCOON-SERUM-30", "Cocoon Serum Bí Đao 30ml",
            "Mô tả", "Chăm sóc da", "Cocoon", null);
    private static final StoreAvailabilityResponse AT_GO_VAP =
            new StoreAvailabilityResponse(2L, 2L, "OneShop Gò Vấp", new BigDecimal("135000.00"), true);
    private static final StoreAvailabilityResponse AT_THU_DUC =
            new StoreAvailabilityResponse(1L, 1L, "OneShop Thủ Đức", new BigDecimal("129000.00"), false);

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void stubs() {
        when(storeService.findSelectableStore(2L)).thenReturn(Optional.of(GO_VAP));
        when(storeService.requireSelectableStore(2L)).thenReturn(GO_VAP);
        when(storeService.requireSelectableStore(5L)).thenThrow(new BadRequestException("storeId", "inactive"));
        when(storeService.requireSelectableStore(isNull())).thenThrow(new BadRequestException("storeId", "missing"));
        when(storeProductService.getChainCatalog(any(), any())).thenReturn(new PageImpl<>(List.of(
                new CatalogItemResponse(SERUM, null, List.of(AT_GO_VAP, AT_THU_DUC)))));
        when(storeProductService.getStoreCatalog(eq(2L), any(), any())).thenReturn(new PageImpl<>(List.of(
                new CatalogItemResponse(SERUM, AT_GO_VAP, List.of()))));
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

    private String[] csrf(String path) throws Exception {
        HttpResponse<String> page = get(path);
        Matcher field = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertThat(field.find()).isTrue();
        String cookie = page.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("XSRF-TOKEN=")).map(c -> c.split(";")[0]).findFirst().orElseThrow();
        return new String[]{field.group(1), cookie};
    }

    private static String storeCookieHeader(HttpResponse<String> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith(STORE_COOKIE + "=")).findFirst().orElse(null);
    }

    // ---------------------------------------------------------------- catalog context

    @Test
    void withoutACookieTheCatalogIsTheWholeChain() throws Exception {
        String html = get("/products").body();

        assertThat(html).contains("data-store-scope=\"chain\"", "Cocoon Serum Bí Đao 30ml", "OneShop Gò Vấp",
                "OneShop Thủ Đức", "Còn hàng", "Hết hàng", "Từ ", "129.000 ₫");
        verify(storeProductService).getChainCatalog(any(), any());
        verify(storeProductService, never()).getStoreCatalog(anyLong(), any(), any());
    }

    @Test
    void aValidCookiePutsTheCatalogInThatStore() throws Exception {
        String html = get("/products?q=serum", "Cookie", STORE_COOKIE + "=2").body();

        assertThat(html).contains("data-store-scope=\"store\"", "OneShop Gò Vấp", "135.000 ₫", "Còn hàng")
                .doesNotContain("OneShop Thủ Đức", "129.000 ₫");
        verify(storeProductService).getStoreCatalog(eq(2L), any(), any());
        verify(storeProductService, never()).getChainCatalog(any(), any());
    }

    @Test
    void anUnknownOrMalformedCookieIsDroppedAndTheCatalogIsTheWholeChain() throws Exception {
        for (String value : List.of("999", "abc", "-1")) {
            HttpResponse<String> response = get("/products", "Cookie", STORE_COOKIE + "=" + value);

            assertThat(response.body()).as(value).contains("data-store-scope=\"chain\"");
            assertThat(storeCookieHeader(response)).as(value).startsWith(STORE_COOKIE + "=;").contains("Max-Age=0");
        }
        verify(storeProductService, never()).getStoreCatalog(anyLong(), any(), any());
    }

    @Test
    void productDetailReceivesTheValidatedStoreOnly() throws Exception {
        when(storeProductService.getProductDetail(3L, 2L))
                .thenReturn(new ProductDetailResponse(SERUM, List.of(), AT_GO_VAP, List.of(AT_THU_DUC)));
        when(storeProductService.getProductDetail(eq(3L), isNull()))
                .thenReturn(new ProductDetailResponse(SERUM, List.of(), null, List.of(AT_GO_VAP, AT_THU_DUC)));

        String selected = get("/products/3", "Cookie", STORE_COOKIE + "=2").body();
        assertThat(selected).contains("id=\"selected-store-price\"", "135.000 ₫", "Xem tại chi nhánh khác",
                "OneShop Thủ Đức", "Đổi sang chi nhánh này");

        // a Store that is not valid, or one named in the URL, is never passed on
        String chain = get("/products/3?storeId=2", "Cookie", STORE_COOKIE + "=999").body();
        assertThat(chain).contains("id=\"no-store-selected\"", "Chi nhánh đang bán sản phẩm này");
        verify(storeProductService).getProductDetail(eq(3L), isNull());
    }

    @Test
    void unknownProductIs404() throws Exception {
        when(storeProductService.getProductDetail(eq(404L), any())).thenThrow(new ResourceNotFoundException("Không tìm thấy sản phẩm #404"));

        HttpResponse<String> response = get("/products/404");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("Không tìm thấy sản phẩm #404", "data-layout=\"client\"");
    }

    // ---------------------------------------------------------------- choosing a Store

    @Test
    void selectingAStoreSetsTheCookieOnlyAfterTheServiceAcceptedIt() throws Exception {
        String[] csrf = csrf("/login");

        HttpResponse<String> ok = post("/stores/select", FORM, "storeId=2&returnTo=%2Fproducts%2F3&_csrf=" + csrf[0],
                "Cookie", csrf[1]);
        assertThat(ok.statusCode()).isEqualTo(302);
        assertThat(ok.headers().firstValue("Location").orElseThrow()).endsWith("/products/3");
        assertThat(storeCookieHeader(ok)).startsWith(STORE_COOKIE + "=2;").contains("HttpOnly", "SameSite=Lax");

        for (String body : List.of("storeId=5", "")) {
            HttpResponse<String> refused = post("/stores/select", FORM, body + "&_csrf=" + csrf[0], "Cookie", csrf[1]);
            assertThat(refused.headers().firstValue("Location").orElseThrow()).as(body).endsWith("/stores?error=unavailable");
            assertThat(storeCookieHeader(refused)).as(body).isNull();
        }
    }

    @Test
    void selectingAStoreWithoutCsrfTokenIsRejected() throws Exception {
        HttpResponse<String> response = post("/stores/select", FORM, "storeId=2");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(storeCookieHeader(response)).isNull();
        verify(storeService, never()).requireSelectableStore(any());
    }

    @Test
    void storeFinderIsPublicAndPassesTheFiltersToTheService() throws Exception {
        when(storeService.findStores("Đà Nẵng", "Hải Châu")).thenReturn(List.of());

        HttpResponse<String> response = get("/stores?provinceCity=%C4%90%C3%A0%20N%E1%BA%B5ng&area=H%E1%BA%A3i%20Ch%C3%A2u");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Hệ thống cửa hàng", "Không có chi nhánh nào phù hợp.");
        verify(storeService).findStores("Đà Nẵng", "Hải Châu");
    }

    // ---------------------------------------------------------------- Admin catalog is closed by the backend

    @Test
    void adminCatalogPagesNeedTheAdminRole() throws Exception {
        String customer = cookieOf("customer@oneshop.test", RoleName.CUSTOMER);
        String staff = cookieOf("staff@oneshop.test", RoleName.STAFF);
        String admin = cookieOf("admin@oneshop.test", RoleName.ADMIN);

        for (String path : List.of("/admin/categories", "/admin/brands", "/admin/products", "/admin/products/new",
                "/admin/stores", "/admin/stores/new", "/admin/store-products", "/admin/store-products/new")) {
            assertThat(get(path).statusCode()).as("guest " + path).isEqualTo(302);
            assertThat(get(path, "Cookie", customer).statusCode()).as("customer " + path).isEqualTo(403);
            assertThat(get(path, "Cookie", staff).statusCode()).as("staff " + path).isEqualTo(403);
        }
        // nothing was read for them
        verify(productService, never()).getAllCategories();
        verify(storeService, never()).getAllStores();

        assertThat(get("/admin/categories", "Cookie", admin).statusCode()).isEqualTo(200);
        assertThat(get("/admin/stores", "Cookie", admin).body()).contains("data-layout=\"admin\"", "Thêm chi nhánh");
    }

    @Test
    void adminCatalogWritesNeedTheAdminRoleAndACsrfToken() throws Exception {
        String customer = cookieOf("customer@oneshop.test", RoleName.CUSTOMER);
        String admin = cookieOf("admin@oneshop.test", RoleName.ADMIN);
        String[] csrf = csrf("/login");
        String product = "sku=NEW-1&name=New&categoryId=1&brandId=1&status=ACTIVE";

        // customer with a valid CSRF token
        assertThat(post("/admin/products", FORM, product + "&_csrf=" + csrf[0], "Cookie", customer + "; " + csrf[1])
                .statusCode()).isEqualTo(403);
        // admin without a CSRF token
        assertThat(post("/admin/products", FORM, product, "Cookie", admin).statusCode()).isEqualTo(403);
        // guest
        assertThat(post("/admin/products", FORM, product + "&_csrf=" + csrf[0], "Cookie", csrf[1]).statusCode())
                .isEqualTo(302);

        verify(productService, never()).createProduct(any());
        verifyNoInteractions(roleRepository);
    }
}
