package com.oneshop;

import com.oneshop.dto.response.ProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class WebRoutesIntegrationTest extends AbstractIntegrationTest {

    @BeforeEach
    void stubProducts() {
        when(productService.getActiveProducts(any())).thenReturn(new PageImpl<>(List.of(
                new ProductResponse(1L, "Kem dưỡng ẩm", "Mô tả", new BigDecimal("199000"), null))));
    }

    @Test
    void homePageIsRenderedByThymeleafAndDecoratedBySiteMesh() throws Exception {
        HttpResponse<String> response = get("/");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).contains("text/html"));
        String html = response.body();
        // content of the page itself (Thymeleaf template home/index.html)
        assertThat(html).contains("Chào mừng đến với OneShop");
        // wrapper added by the SiteMesh decorator (layouts/main.html + fragments)
        assertThat(html).contains("data-layout=\"sitemesh\"", "os-topbar", "navbar", "os-footer");
        assertThat(html).contains("<title>Trang chủ | OneShop</title>");
        // Bootstrap is served locally
        assertThat(html).contains("/vendor/bootstrap/bootstrap.min.css");
        assertThat(count(html, "<title>")).isEqualTo(1);
    }

    @Test
    void basicPagesRenderInsideTheLayout() throws Exception {
        for (String path : List.of("/login", "/register", "/products", "/cart")) {
            HttpResponse<String> response = get(path);

            assertThat(response.statusCode()).as(path).isEqualTo(200);
            assertThat(response.body()).as(path).contains("data-layout=\"sitemesh\"", "os-footer");
        }
        assertThat(get("/products").body()).contains("Kem dưỡng ẩm", "199.000 ₫");
        assertThat(get("/login").body()).contains("Đăng nhập").contains("name=\"_csrf\"");
        assertThat(get("/cart").body()).contains("Giỏ hàng");
    }

    @Test
    void healthEndpointIsPublicAndNotDecorated() throws Exception {
        HttpResponse<String> response = get("/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    void staticAssetsAreServed() throws Exception {
        assertThat(get("/vendor/bootstrap/bootstrap.min.css").statusCode()).isEqualTo(200);
        assertThat(get("/css/main.css").statusCode()).isEqualTo(200);
        assertThat(get("/js/main.js").statusCode()).isEqualTo(200);
    }

    @Test
    void decoratorIsNotReachableDirectly() throws Exception {
        // never served to the outside world: anonymous users are sent to the login page, nobody gets a 200
        HttpResponse<String> response = get("/decorators/main");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.body()).doesNotContain("data-layout");
    }

    @Test
    void protectedWebRoutesRedirectAnonymousUsersToLogin() throws Exception {
        HttpResponse<String> response = get("/admin");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location")).hasValueSatisfying(v -> assertThat(v).endsWith("/login"));
    }

    @Test
    void protectedApiRoutesAnswer401ForAnonymousUsers() throws Exception {
        assertThat(get("/api/admin/anything").statusCode()).isEqualTo(401);
    }

    private static int count(String text, String token) {
        return text.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }
}
