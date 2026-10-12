package com.oneshop;

import com.oneshop.dto.response.AdminProductResponse;
import com.oneshop.dto.response.BrandResponse;
import com.oneshop.dto.response.CategoryResponse;
import com.oneshop.entity.ProductStatus;
import com.oneshop.entity.Role;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.entity.VisibilityStatus;
import com.oneshop.exception.WebExceptionHandler;
import com.oneshop.security.jwt.JwtService;
import com.oneshop.security.service.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class MaxUploadSizeExceededIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN = "admin-upload@oneshop.test";
    private static final String PASSWORD = "Password123";
    private static final String COOKIE = "ONESHOP_TOKEN";

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private WebExceptionHandler webExceptionHandler;

    @BeforeEach
    void setupAdmin() {
        User u = new User();
        u.setEmail(ADMIN);
        u.setFullName("Admin Upload Tester");
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setRole(new Role(RoleName.ADMIN));
        when(userRepository.findByEmail(ADMIN)).thenReturn(Optional.of(u));
        withId(u);
    }

    private String[] asAdmin() {
        return new String[]{"Cookie", COOKIE + "=" + jwtService.generateToken(userDetailsService.loadUserByUsername(ADMIN))};
    }

    // 1. Unit tests for WebExceptionHandler redirect resolution and flash message logic

    @Test
    void handlerRedirectsToProductFormWithVietnameseMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/products/12/images");
        FlashMap flashMap = new FlashMap();
        request.setAttribute(org.springframework.web.servlet.DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, flashMap);

        String view = webExceptionHandler.handleMaxUploadSizeExceeded(request, new MaxUploadSizeExceededException(6_000_000));

        assertThat(view).isEqualTo("redirect:/admin/products/12/edit?error=image-size");
        assertThat(flashMap.get("error")).isEqualTo("image-size");
        assertThat((String) flashMap.get("errorMessage")).contains("Ảnh vượt quá 5MB. Vui lòng chọn tệp nhỏ hơn.");
    }

    @Test
    void handlerRedirectsToBrandFormWithVietnameseMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/brands/5/logo");
        FlashMap flashMap = new FlashMap();
        request.setAttribute(org.springframework.web.servlet.DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, flashMap);

        String view = webExceptionHandler.handleMaxUploadSizeExceeded(request, new MaxUploadSizeExceededException(6_000_000));

        assertThat(view).isEqualTo("redirect:/admin/brands/5/edit?error=image-size");
        assertThat(flashMap.get("error")).isEqualTo("image-size");
        assertThat((String) flashMap.get("errorMessage")).contains("Ảnh vượt quá 5MB. Vui lòng chọn tệp nhỏ hơn.");
    }

    @Test
    void handlerRedirectsToCategoryFormWithVietnameseMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/categories/3/image");
        FlashMap flashMap = new FlashMap();
        request.setAttribute(org.springframework.web.servlet.DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, flashMap);

        String view = webExceptionHandler.handleMaxUploadSizeExceeded(request, new MaxUploadSizeExceededException(6_000_000));

        assertThat(view).isEqualTo("redirect:/admin/categories/3/edit?error=image-size");
        assertThat(flashMap.get("error")).isEqualTo("image-size");
        assertThat((String) flashMap.get("errorMessage")).contains("Ảnh vượt quá 5MB. Vui lòng chọn tệp nhỏ hơn.");
    }

    @Test
    void handlerRedirectsToCategoryListWhenAddingNewCategory() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/categories");
        request.setParameter("name", "New Skin Care");
        request.setParameter("description", "Category desc");
        FlashMap flashMap = new FlashMap();
        request.setAttribute(org.springframework.web.servlet.DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, flashMap);

        String view = webExceptionHandler.handleMaxUploadSizeExceeded(request, new MaxUploadSizeExceededException(6_000_000));

        assertThat(view).isEqualTo("redirect:/admin/categories?error=image-size");
        assertThat(flashMap.get("error")).isEqualTo("image-size");
        assertThat((String) flashMap.get("errorMessage")).contains("Ảnh vượt quá 5MB. Vui lòng chọn tệp nhỏ hơn.");
        assertThat(flashMap.get("form")).isNotNull();
    }

    // 2. Integration tests: Rendering the error on the 3 admin forms

    @Test
    void productImageFormRendersVietnameseSizeErrorAndPreservesData() throws Exception {
        when(productService.getProductForAdmin(12L)).thenReturn(new AdminProductResponse(
                12L, "SKU-PROD-12", "Sản phẩm thử nghiệm", "Mô tả sản phẩm", 1L, "Chăm sóc da", 1L, "Cocoon", ProductStatus.ACTIVE
        ));
        when(productService.getAllCategories()).thenReturn(List.of(new CategoryResponse(1L, "Chăm sóc da", "", null, VisibilityStatus.ACTIVE)));
        when(productService.getAllBrands()).thenReturn(List.of(new BrandResponse(1L, "Cocoon", "", null, VisibilityStatus.ACTIVE)));
        when(productService.getProductImages(12L)).thenReturn(Collections.emptyList());

        HttpResponse<String> response = get("/admin/products/12/edit?error=image-size", asAdmin());

        assertThat(response.statusCode()).isEqualTo(200);
        String body = response.body();
        assertThat(body).contains("Ảnh vượt quá 5MB. Vui lòng chọn tệp nhỏ hơn.");
        assertThat(body).contains("SKU-PROD-12");
        assertThat(body).contains("Sản phẩm thử nghiệm");
    }

    @Test
    void brandFormRendersVietnameseSizeErrorAndPreservesData() throws Exception {
        when(productService.getBrand(5L)).thenReturn(new BrandResponse(
                5L, "Thương hiệu thử nghiệm", "Mô tả thương hiệu", null, VisibilityStatus.ACTIVE
        ));
        when(productService.getAllBrands()).thenReturn(List.of(new BrandResponse(5L, "Thương hiệu thử nghiệm", "", null, VisibilityStatus.ACTIVE)));

        HttpResponse<String> response = get("/admin/brands/5/edit?error=image-size", asAdmin());

        assertThat(response.statusCode()).isEqualTo(200);
        String body = response.body();
        assertThat(body).contains("Ảnh vượt quá 5MB. Vui lòng chọn tệp nhỏ hơn.");
        assertThat(body).contains("Thương hiệu thử nghiệm");
        assertThat(body).contains("tả thương hiệu");
    }

    @Test
    void categoryFormRendersVietnameseSizeErrorAndPreservesData() throws Exception {
        when(productService.getCategory(3L)).thenReturn(new CategoryResponse(
                3L, "Chăm sóc tóc", "Mô tả danh mục", null, VisibilityStatus.ACTIVE
        ));
        when(productService.getAllCategories()).thenReturn(List.of(new CategoryResponse(3L, "Chăm sóc tóc", "", null, VisibilityStatus.ACTIVE)));

        HttpResponse<String> response = get("/admin/categories/3/edit?error=image-size", asAdmin());

        assertThat(response.statusCode()).isEqualTo(200);
        String body = response.body();
        assertThat(body).contains("Ảnh vượt quá 5MB. Vui lòng chọn tệp nhỏ hơn.");
        assertThat(body).contains("Chăm sóc tóc");
        assertThat(body).contains("tả danh mục");
    }
}
