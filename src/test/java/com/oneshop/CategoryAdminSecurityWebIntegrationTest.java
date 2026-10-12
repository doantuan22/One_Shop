package com.oneshop;

import com.oneshop.dto.response.CategoryResponse;
import com.oneshop.entity.Role;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.entity.VisibilityStatus;
import com.oneshop.security.jwt.JwtService;
import com.oneshop.security.service.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class CategoryAdminSecurityWebIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Password123";
    private static final String COOKIE = "ONESHOP_TOKEN";
    private static final String CUSTOMER = "customer@oneshop.test";
    private static final String STAFF = "staff@oneshop.test";
    private static final String ADMIN = "admin@oneshop.test";

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setupAccounts() {
        user(CUSTOMER, RoleName.CUSTOMER);
        user(STAFF, RoleName.STAFF);
        user(ADMIN, RoleName.ADMIN);
    }

    private User user(String email, RoleName role) {
        User u = new User();
        u.setEmail(email);
        u.setFullName(email);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setRole(new Role(role));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        withId(u);
        return u;
    }

    private String[] as(String email) {
        return new String[]{"Cookie", COOKIE + "=" + jwtService.generateToken(userDetailsService.loadUserByUsername(email))};
    }

    @Test
    void guestIsRedirectedToLogin() throws Exception {
        HttpResponse<String> getResp = get("/admin/categories");
        assertThat(getResp.statusCode()).isEqualTo(302);
        assertThat(getResp.headers().firstValue("Location").orElseThrow()).endsWith("/login");

        HttpResponse<String> postResp = post("/admin/categories", "application/x-www-form-urlencoded", "name=Test");
        assertThat(postResp.statusCode()).isIn(302, 401, 403);
    }

    @Test
    void customerIsForbidden() throws Exception {
        assertThat(get("/admin/categories", as(CUSTOMER)).statusCode()).isEqualTo(403);
        assertThat(post("/admin/categories", "application/x-www-form-urlencoded", "name=Test", as(CUSTOMER)).statusCode()).isEqualTo(403);
    }

    @Test
    void staffIsForbidden() throws Exception {
        assertThat(get("/admin/categories", as(STAFF)).statusCode()).isEqualTo(403);
        assertThat(post("/admin/categories", "application/x-www-form-urlencoded", "name=Test", as(STAFF)).statusCode()).isEqualTo(403);
    }

    @Test
    void adminPostWithoutCsrfIsRejectedWith403() throws Exception {
        HttpResponse<String> response = post("/admin/categories", "application/x-www-form-urlencoded",
                "name=NewCategory&status=ACTIVE", as(ADMIN));
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void adminCanAccessCategoriesListAndRendersTableAndUploadControls() throws Exception {
        when(productService.getAllCategories()).thenReturn(List.of(
                new CategoryResponse(1L, "Chăm sóc da", "Mô tả 1", "/images/site/categories/category-skincare.webp", VisibilityStatus.ACTIVE),
                new CategoryResponse(2L, "Trang điểm", "Mô tả 2", null, VisibilityStatus.ACTIVE)
        ));

        HttpResponse<String> response = get("/admin/categories", as(ADMIN));
        assertThat(response.statusCode()).isEqualTo(200);

        String html = response.body();
        // Table headers and columns
        assertThat(html).contains("Danh mục dùng chung toàn chuỗi");
        assertThat(html).contains("Chăm sóc da");
        assertThat(html).contains("Trang điểm");

        // First category has thumbnail img
        assertThat(html).contains("/images/site/categories/category-skincare.webp");

        // Second category has neutral placeholder dash
        assertThat(html).contains("rounded border d-inline-flex align-items-center justify-content-center bg-light text-secondary small");

        // File upload input and hints
        assertThat(html).contains("type=\"file\"");
        assertThat(html).contains("accept=\"image/jpeg,image/png,image/webp\"");
        assertThat(html).contains("enctype=\"multipart/form-data\"");
        assertThat(html).contains("Dung lượng tối đa: 5MB");
    }
}
