package com.oneshop;

import com.oneshop.dto.response.StoreResponse;
import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.Role;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.security.jwt.JwtService;
import com.oneshop.security.service.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Phase 4: the three SiteMesh layouts (client, staff, admin) wrap their own pages and show the Store context. The
 * selected Store comes from the ONESHOP_STORE cookie through SelectedStoreInterceptor and the (mocked) StoreService; the
 * Staff scope comes from the (mocked) StoreService through StaffStoreScopeInterceptor.
 */
class LayoutsIntegrationTest extends AbstractIntegrationTest {

    private static final StoreResponse STORE = new StoreResponse(1L, "TD", "OneShop Thủ Đức", "1 Võ Văn Ngân",
            "TP.HCM", "Thủ Đức", null, null, true, true, ActiveStatus.ACTIVE);

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    private String staffToken;
    private String adminToken;

    @BeforeEach
    void users() {
        staffToken = tokenFor("staff@oneshop.test", RoleName.STAFF);
        adminToken = tokenFor("admin@oneshop.test", RoleName.ADMIN);
        // the Staff of these tests is assigned to one Store (the unassigned case is in AccessControlIntegrationTest)
        when(storeService.getAssignedStores("staff@oneshop.test")).thenReturn(List.of(STORE));
    }

    private String tokenFor(String email, RoleName role) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(email);
        user.setPasswordHash("x");
        user.setRole(new Role(role));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        return jwtService.generateToken(userDetailsService.loadUserByUsername(email));
    }

    private HttpResponse<String> asStaff(String path) throws Exception {
        return get(path, "Authorization", "Bearer " + staffToken);
    }

    private HttpResponse<String> asAdmin(String path) throws Exception {
        return get(path, "Authorization", "Bearer " + adminToken);
    }

    @Test
    void clientLayoutShowsWholeChainByDefault() throws Exception {
        String html = get("/").body();

        assertThat(html).contains("data-layout=\"client\"", "os-topbar", "os-footer");
        assertThat(html).contains("Chi nhánh đang chọn:", "id=\"store-context\"", "data-store-scope=\"chain\"", "Toàn chuỗi");
        assertThat(html).doesNotContain("data-layout=\"staff\"", "data-layout=\"admin\"", "os-sidebar");
    }

    @Test
    void clientLayoutShowsSelectedStoreWhenOneIsChosen() throws Exception {
        when(storeService.findSelectableStore(1L)).thenReturn(Optional.of(STORE));

        String html = get("/", "Cookie", "ONESHOP_STORE=1").body();

        assertThat(html).contains("data-store-scope=\"store\"", "OneShop Thủ Đức", "Đổi chi nhánh", "Xem toàn chuỗi");
        assertThat(html).doesNotContain("data-store-scope=\"chain\"");
    }

    @Test
    void staffAreaUsesTheStaffLayout() throws Exception {
        HttpResponse<String> response = asStaff("/staff");

        assertThat(response.statusCode()).isEqualTo(200);
        String html = response.body();
        assertThat(html).contains("data-layout=\"staff\"", "os-sidebar", "id=\"staff-store-scope\"",
                "Chi nhánh được phân công", "OneShop Thủ Đức");
        assertThat(html).contains("<title>Tổng quan chi nhánh | OneShop Staff</title>", "Đơn cần xử lý");
        // not the client chrome, and the sample page's own table parts are rendered once (inside the table)
        assertThat(html).doesNotContain("data-layout=\"client\"", "os-topbar", "os-footer", "Toàn chuỗi");
        assertThat(count(html, "id=\"orders-body\"")).isEqualTo(1);
        assertThat(count(html, "<table")).isEqualTo(1);
        assertThat(count(html, "<title>")).isEqualTo(1);
    }

    @Test
    void staffLayoutShowsTheAssignedStore() throws Exception {
        String html = asStaff("/staff").body();

        assertThat(html).contains("OneShop Thủ Đức", "TD · Thủ Đức");
        assertThat(html).doesNotContain("Chưa được phân công");
    }

    @Test
    void staffMenuMarksCurrentPageAndDisablesPagesNotBuiltYet() throws Exception {
        String html = asStaff("/staff").body();

        assertThat(html).contains("os-nav-link active", "aria-current=\"page\"", "aria-disabled=\"true\"", "Sắp có");
        assertThat(count(html, "aria-current=\"page\"")).isEqualTo(1);
    }

    @Test
    void adminAreaUsesTheAdminLayoutWithChainWideScope() throws Exception {
        HttpResponse<String> response = asAdmin("/admin");

        assertThat(response.statusCode()).isEqualTo(200);
        String html = response.body();
        assertThat(html).contains("data-layout=\"admin\"", "os-sidebar", "id=\"admin-scope-badge\"", "Toàn chuỗi cửa hàng",
                "Phân công nhân viên", "Tồn kho toàn chuỗi");
        assertThat(html).contains("<title>Tổng quan toàn chuỗi | OneShop Admin</title>");
        assertThat(html).doesNotContain("data-layout=\"client\"", "os-topbar", "os-footer");
        assertThat(count(html, "<title>")).isEqualTo(1);
    }

    @Test
    void adminSampleRendersEverySharedComponent() throws Exception {
        String html = asAdmin("/admin").body();

        // card + stat card
        assertThat(html).contains("card-header", "os-stat__value", "Lọc theo chi nhánh");
        // form field + select (options handed in by the page)
        assertThat(html).contains("id=\"filter-keyword\"", "name=\"keyword\"", "id=\"filter-store\"", "name=\"storeId\"",
                "<option value=\"\">Tất cả chi nhánh</option>");
        // responsive table + empty row
        assertThat(html).contains("table-responsive", "os-table", "Chưa có đơn hàng nào.");
        // modal with its body and footer
        assertThat(html).contains("class=\"modal fade\"", "id=\"confirmModal\"", "aria-labelledby=\"confirmModal-title\"",
                "modal-body", "modal-footer", "data-bs-target=\"#confirmModal\"");
        // the parts handed to the components appear only inside the shared component, not as stray copies
        assertThat(count(html, "id=\"orders-head\"")).isEqualTo(1);
        assertThat(count(html, "<table")).isEqualTo(1);
        assertThat(count(html, "<form")).isEqualTo(2); // filter form + logout form
    }

    @Test
    void layoutsAreResponsive() throws Exception {
        for (String html : List.of(get("/").body(), asStaff("/staff").body(), asAdmin("/admin").body())) {
            assertThat(html).contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">",
                    "/vendor/bootstrap/bootstrap.min.css", "/vendor/bootstrap/bootstrap.bundle.min.js", "/css/main.css");
        }
        for (String html : List.of(asStaff("/staff").body(), asAdmin("/admin").body())) {
            // sidebar collapses into an off-canvas menu on small screens
            assertThat(html).contains("offcanvas-lg", "data-bs-toggle=\"offcanvas\"", "data-bs-target=\"#appSidebar\"");
        }
    }

    @Test
    void staffAndAdminAreasKeepTheirAccessRules() throws Exception {
        assertThat(get("/staff").statusCode()).isEqualTo(302);
        assertThat(asStaff("/admin").statusCode()).isEqualTo(403);
        // the staff area is scoped to assigned Stores, so it is STAFF only
        assertThat(asAdmin("/staff").statusCode()).isEqualTo(403);
    }

    private static int count(String text, String token) {
        return text.split(Pattern.quote(token), -1).length - 1;
    }
}
