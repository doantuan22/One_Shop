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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 5: role-based route protection, Staff Store scope, CSRF and logout, all decided by the backend. Every request
 * here goes straight to the URL, the way a user typing it would: no menu is involved.
 */
class AccessControlIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Password123";
    private static final String COOKIE = "ONESHOP_TOKEN";
    private static final String CUSTOMER = "customer@oneshop.test";
    private static final String STAFF = "staff@oneshop.test";
    private static final String UNASSIGNED_STAFF = "new.staff@oneshop.test";
    private static final String ADMIN = "admin@oneshop.test";

    private static final StoreResponse STORE = new StoreResponse(1L, "TD", "OneShop Thủ Đức", "1 Võ Văn Ngân",
            "TP.HCM", "Thủ Đức", null, null, true, true, ActiveStatus.ACTIVE);

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void accounts() {
        user(CUSTOMER, RoleName.CUSTOMER);
        user(STAFF, RoleName.STAFF);
        user(UNASSIGNED_STAFF, RoleName.STAFF);
        user(ADMIN, RoleName.ADMIN);
        when(storeService.getAssignedStores(STAFF)).thenReturn(List.of(STORE));
    }

    private User user(String email, RoleName role) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(new Role(role));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        return user;
    }

    /** The JWT cookie a browser would send after logging in as this account. */
    private String[] as(String email) {
        return new String[]{"Cookie", COOKIE + "=" + jwtService.generateToken(userDetailsService.loadUserByUsername(email))};
    }

    private int status(String path, String... headers) throws Exception {
        return get(path, headers).statusCode();
    }

    // ---------------------------------------------------------------- route protection by role

    @Test
    void guestsAreSentToLoginForEveryProtectedArea() throws Exception {
        for (String path : List.of("/cart", "/staff", "/staff/orders", "/admin", "/admin/stores", "/anything-else")) {
            HttpResponse<String> response = get(path);

            assertThat(response.statusCode()).as(path).isEqualTo(302);
            assertThat(response.headers().firstValue("Location").orElseThrow()).as(path).endsWith("/login");
        }
        assertThat(status("/api/staff/orders")).isEqualTo(401);
        assertThat(status("/api/admin/stores")).isEqualTo(401);
    }

    @Test
    void publicPagesNeedNoLogin() throws Exception {
        for (String path : List.of("/", "/login", "/register", "/health")) {
            assertThat(status(path)).as(path).isEqualTo(200);
        }
    }

    @Test
    void customerReachesTheClientAreaOnly() throws Exception {
        assertThat(status("/cart", as(CUSTOMER))).isEqualTo(200);
        assertThat(status("/staff", as(CUSTOMER))).isEqualTo(403);
        assertThat(status("/staff/orders", as(CUSTOMER))).isEqualTo(403);
        assertThat(status("/api/staff/orders", as(CUSTOMER))).isEqualTo(403);
        assertThat(status("/admin", as(CUSTOMER))).isEqualTo(403);
        assertThat(status("/api/admin/stores", as(CUSTOMER))).isEqualTo(403);
    }

    @Test
    void staffReachesTheStaffAreaOnly() throws Exception {
        assertThat(status("/staff", as(STAFF))).isEqualTo(200);
        assertThat(status("/admin", as(STAFF))).isEqualTo(403);
        assertThat(status("/admin/stores", as(STAFF))).isEqualTo(403);
        assertThat(status("/api/admin/stores", as(STAFF))).isEqualTo(403);
        // a Staff account is not a shopper
        assertThat(status("/cart", as(STAFF))).isEqualTo(403);
    }

    @Test
    void adminReachesTheAdminAreaOnly() throws Exception {
        assertThat(status("/admin", as(ADMIN))).isEqualTo(200);
        // the Staff area is scoped to assigned Stores; Admin manages the chain from /admin
        assertThat(status("/staff", as(ADMIN))).isEqualTo(403);
        assertThat(status("/api/staff/orders", as(ADMIN))).isEqualTo(403);
        assertThat(status("/cart", as(ADMIN))).isEqualTo(403);
    }

    @Test
    void forbiddenPagesRenderTheErrorPageNotTheProtectedContent() throws Exception {
        // as a browser asks for it (without Accept: text/html the same 403 comes back as JSON)
        HttpResponse<String> response = get("/admin", "Accept", "text/html", as(CUSTOMER)[0], as(CUSTOMER)[1]);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("Bạn không có quyền truy cập");
        assertThat(response.body()).doesNotContain("data-layout=\"admin\"", "Tổng quan toàn chuỗi");
    }

    @Test
    void decoratorsCannotBeRequestedDirectlyEvenByAdmin() throws Exception {
        for (String path : List.of("/decorators/client", "/decorators/staff", "/decorators/admin")) {
            assertThat(status(path, as(ADMIN))).as(path).isEqualTo(403);
        }
    }

    // ---------------------------------------------------------------- the role comes from the database, per request

    @Test
    void disabledAccountLosesAccessEvenWithAStillValidToken() throws Exception {
        String[] cookie = as(CUSTOMER);
        assertThat(status("/cart", cookie)).isEqualTo(200);

        user(CUSTOMER, RoleName.CUSTOMER).setStatus(ActiveStatus.INACTIVE);

        assertThat(status("/cart", cookie)).isEqualTo(302);
    }

    @Test
    void roleInsideTheTokenIsNotTrusted() throws Exception {
        // token issued while the account was ADMIN ...
        String[] cookie = as(ADMIN);
        assertThat(status("/admin", cookie)).isEqualTo(200);

        // ... the account is then demoted in the database
        user(ADMIN, RoleName.CUSTOMER);

        assertThat(status("/admin", cookie)).isEqualTo(403);
    }

    // ---------------------------------------------------------------- Staff Store scope (BR-14)

    @Test
    void assignedStaffSeesTheirStore() throws Exception {
        HttpResponse<String> response = get("/staff", as(STAFF));

        assertThat(response.body()).contains("OneShop Thủ Đức", "Đơn cần xử lý");
        assertThat(response.body()).doesNotContain("id=\"no-store-scope\"", "Chưa được phân công");
    }

    @Test
    void staffWithoutActiveAssignmentGetsNoStoreData() throws Exception {
        HttpResponse<String> landing = get("/staff", as(UNASSIGNED_STAFF));

        // the landing page only explains the situation: no Store, no Store sections
        assertThat(landing.statusCode()).isEqualTo(200);
        assertThat(landing.body()).contains("id=\"no-store-scope\"", "Chưa được phân công");
        assertThat(landing.body()).doesNotContain("Đơn cần xử lý", "<table", "OneShop Thủ Đức");

        // everything else in the Staff area is refused by the backend
        assertThat(status("/staff/orders", as(UNASSIGNED_STAFF))).isEqualTo(403);
        assertThat(status("/staff/stock", as(UNASSIGNED_STAFF))).isEqualTo(403);
        assertThat(status("/api/staff/orders", as(UNASSIGNED_STAFF))).isEqualTo(403);
    }

    @Test
    void assignedStaffPassesTheScopeCheckOnInnerPages() throws Exception {
        // no such page yet (Phase 10): the request gets past security and the scope check and ends as 404, not 403
        assertThat(status("/staff/orders", as(STAFF))).isEqualTo(404);
    }

    @Test
    void staffCannotChooseTheirStoreThroughTheRequest() throws Exception {
        String wanted = "/staff?storeId=2&store_id=2&assignedStores=2";

        // unassigned Staff stays without a Store whatever the request says
        HttpResponse<String> unassigned = get(wanted, as(UNASSIGNED_STAFF));
        assertThat(unassigned.body()).contains("id=\"no-store-scope\"");
        assertThat(status("/staff/orders?storeId=1", as(UNASSIGNED_STAFF))).isEqualTo(403);
        assertThat(status("/staff/orders", "X-Store-Id", "1", as(UNASSIGNED_STAFF)[0], as(UNASSIGNED_STAFF)[1]))
                .isEqualTo(403);

        // assigned Staff keeps exactly the Store of the assignment
        HttpResponse<String> assigned = get(wanted, as(STAFF));
        assertThat(assigned.body()).contains("OneShop Thủ Đức", "TD · Thủ Đức");

        // the scope was looked up by the authenticated e-mail only
        verify(storeService, never()).getAssignedStores("2");
    }

    // ---------------------------------------------------------------- login, CSRF, logout

    @Test
    void webLoginSendsEachRoleToItsArea() throws Exception {
        assertThat(webLogin(CUSTOMER).headers().firstValue("Location").orElseThrow()).endsWith("/");
        assertThat(webLogin(STAFF).headers().firstValue("Location").orElseThrow()).endsWith("/staff");
        assertThat(webLogin(ADMIN).headers().firstValue("Location").orElseThrow()).endsWith("/admin");
    }

    @Test
    void jwtCookieIsHttpOnlyAndNeverExposedInThePage() throws Exception {
        HttpResponse<String> login = webLogin(STAFF);
        String cookie = jwtCookie(login);

        assertThat(cookie).contains("HttpOnly", "SameSite=Lax", "Path=/");
        String token = cookie.split(";")[0].substring((COOKIE + "=").length());
        assertThat(login.body()).doesNotContain(token);
        assertThat(get("/staff", "Cookie", COOKIE + "=" + token).body()).doesNotContain(token);
    }

    @Test
    void stateChangingRequestWithTheJwtCookieButNoCsrfTokenIsRejected() throws Exception {
        // what a cross-site form post would look like: the browser attaches the cookie, the attacker has no CSRF token
        HttpResponse<String> logout = post("/logout", "application/x-www-form-urlencoded", "", as(CUSTOMER));

        assertThat(logout.statusCode()).isEqualTo(403);
        assertThat(logout.headers().allValues("Set-Cookie")).noneMatch(c -> c.startsWith(COOKIE + "="));
    }

    @Test
    void logoutNeedsPostAndClearsTheJwtCookie() throws Exception {
        String jwt = as(CUSTOMER)[1];
        // GET /logout does not log out (and is not a public page)
        assertThat(get("/logout", "Cookie", jwt).headers().allValues("Set-Cookie"))
                .noneMatch(c -> c.startsWith(COOKIE + "="));

        // the logout form of the page carries the CSRF token
        HttpResponse<String> page = get("/", "Cookie", jwt);
        String csrfField = csrfField(page);
        String csrfCookie = csrfCookie(page);

        HttpResponse<String> logout = post("/logout", "application/x-www-form-urlencoded", "_csrf=" + csrfField,
                "Cookie", jwt + "; " + csrfCookie);

        assertThat(logout.statusCode()).isEqualTo(302);
        assertThat(logout.headers().firstValue("Location").orElseThrow()).endsWith("/login?logout");
        String cleared = logout.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith(COOKIE + "=")).findFirst().orElseThrow();
        assertThat(cleared).startsWith(COOKIE + "=;").contains("Max-Age=0");
    }

    // ---------------------------------------------------------------- registration

    @Test
    void registrationAlwaysCreatesACustomerWithAHashedPassword() throws Exception {
        when(roleRepository.findByName(RoleName.CUSTOMER)).thenReturn(Optional.of(new Role(RoleName.CUSTOMER)));
        when(roleRepository.findByName(RoleName.ADMIN)).thenReturn(Optional.of(new Role(RoleName.ADMIN)));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HttpResponse<String> form = get("/register");
        // a visitor trying to pick their own role
        String body = "fullName=New+User&email=New.User%40oneshop.test&password=" + PASSWORD
                + "&role=ADMIN&role.name=ADMIN&roleId=3&_csrf=" + csrfField(form);
        HttpResponse<String> response = post("/register", "application/x-www-form-urlencoded", body,
                "Cookie", csrfCookie(form));

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElseThrow()).endsWith("/login?registered");
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole().getName()).isEqualTo(RoleName.CUSTOMER);
        assertThat(saved.getValue().getEmail()).isEqualTo("new.user@oneshop.test");
        assertThat(saved.getValue().getPasswordHash()).startsWith("$2").doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void registrationRejectsATakenEmailAndWeakInput() throws Exception {
        when(userRepository.existsByEmail(CUSTOMER)).thenReturn(true);

        HttpResponse<String> form = get("/register");
        HttpResponse<String> taken = post("/register", "application/x-www-form-urlencoded",
                "fullName=Someone&email=" + CUSTOMER.replace("@", "%40") + "&password=" + PASSWORD
                        + "&_csrf=" + csrfField(form), "Cookie", csrfCookie(form));
        assertThat(taken.statusCode()).isEqualTo(200);
        assertThat(taken.body()).contains("Email đã được sử dụng");

        HttpResponse<String> weak = post("/register", "application/x-www-form-urlencoded",
                "fullName=Someone&email=someone%40oneshop.test&password=short&_csrf=" + csrfField(form),
                "Cookie", csrfCookie(form));
        assertThat(weak.statusCode()).isEqualTo(200);
        assertThat(weak.body()).contains("Mật khẩu từ 8 đến 72 ký tự");

        verify(userRepository, never()).save(any(User.class));
    }

    // ---------------------------------------------------------------- helpers

    private HttpResponse<String> webLogin(String email) throws Exception {
        HttpResponse<String> form = get("/login");
        HttpResponse<String> login = post("/login", "application/x-www-form-urlencoded",
                "email=" + email.replace("@", "%40") + "&password=" + PASSWORD + "&_csrf=" + csrfField(form),
                "Cookie", csrfCookie(form));
        assertThat(login.statusCode()).isEqualTo(302);
        return login;
    }

    private static String jwtCookie(HttpResponse<String> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith(COOKIE + "=")).findFirst().orElseThrow();
    }

    private static String csrfField(HttpResponse<String> page) {
        Matcher csrf = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertThat(csrf.find()).as("page has a CSRF form field").isTrue();
        return csrf.group(1);
    }

    private static String csrfCookie(HttpResponse<String> page) {
        return page.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("XSRF-TOKEN=")).map(c -> c.split(";")[0]).findFirst().orElseThrow();
    }
}
