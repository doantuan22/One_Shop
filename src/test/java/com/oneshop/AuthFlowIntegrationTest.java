package com.oneshop;

import com.oneshop.entity.Role;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.security.jwt.JwtService;
import com.oneshop.security.service.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "customer@oneshop.test";
    private static final String PASSWORD = "Password123";
    private static final String COOKIE = "ONESHOP_TOKEN";

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void stubUser() {
        User user = new User();
        user.setEmail(EMAIL);
        user.setFullName("Test Customer");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(new Role(RoleName.CUSTOMER));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    @Test
    void apiLoginReturnsBearerTokenThatAuthenticatesLaterRequests() throws Exception {
        HttpResponse<String> login = post("/api/auth/login", "application/json",
                "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}");

        assertThat(login.statusCode()).isEqualTo(200);
        Matcher token = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(login.body());
        assertThat(token.find()).isTrue();
        assertThat(login.body()).contains("\"tokenType\":\"Bearer\"", "ROLE_CUSTOMER");

        // Authenticated as a customer: past the login wall, but /api/admin/** needs ROLE_ADMIN.
        HttpResponse<String> admin = get("/api/admin/anything", "Authorization", "Bearer " + token.group(1));
        assertThat(admin.statusCode()).isEqualTo(403);
    }

    @Test
    void apiLoginRejectsWrongPassword() throws Exception {
        HttpResponse<String> login = post("/api/auth/login", "application/json",
                "{\"email\":\"" + EMAIL + "\",\"password\":\"wrong-password\"}");

        assertThat(login.statusCode()).isEqualTo(401);
        assertThat(login.body()).doesNotContain("accessToken");
    }

    @Test
    void apiLoginValidatesInput() throws Exception {
        HttpResponse<String> login = post("/api/auth/login", "application/json", "{\"email\":\"not-an-email\"}");

        assertThat(login.statusCode()).isEqualTo(400);
        assertThat(login.body()).contains("fieldErrors");
    }

    @Test
    void tamperedTokenIsIgnored() throws Exception {
        String token = jwtService.generateToken(userDetailsService.loadUserByUsername(EMAIL));

        HttpResponse<String> response = get("/api/admin/anything", "Authorization", "Bearer " + token + "x");

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void webLoginSetsHttpOnlyJwtCookieAndCookieAuthenticatesPages() throws Exception {
        // 1. Load the form to obtain the CSRF token (form field + cookie)
        HttpResponse<String> form = get("/login");
        Matcher csrf = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(form.body());
        assertThat(csrf.find()).isTrue();
        String csrfCookie = form.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("XSRF-TOKEN=")).map(c -> c.split(";")[0]).findFirst().orElseThrow();

        // 2. Submit credentials
        String body = "email=" + EMAIL + "&password=" + PASSWORD + "&_csrf=" + csrf.group(1);
        HttpResponse<String> login = post("/login", "application/x-www-form-urlencoded", body, "Cookie", csrfCookie);
        assertThat(login.statusCode()).isEqualTo(302);
        String jwtCookie = login.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith(COOKIE + "=")).findFirst().orElseThrow();
        assertThat(jwtCookie).contains("HttpOnly").contains("SameSite=Lax");

        // 3. The cookie authenticates the next page: navbar shows the user and a CSRF-protected logout form
        HttpResponse<String> home = get("/", "Cookie", jwtCookie.split(";")[0]);
        assertThat(home.statusCode()).isEqualTo(200);
        assertThat(home.body()).contains(EMAIL, "Đăng xuất", "action=\"/logout\"", "name=\"_csrf\"");
        assertThat(home.body()).doesNotContain("href=\"/register\"");
    }

    @Test
    void webLoginWithoutCsrfTokenIsRejected() throws Exception {
        HttpResponse<String> login = post("/login", "application/x-www-form-urlencoded",
                "email=" + EMAIL + "&password=" + PASSWORD);

        assertThat(login.statusCode()).isEqualTo(403);
        assertThat(login.headers().allValues("Set-Cookie")).noneMatch(c -> c.startsWith(COOKIE + "="));
    }
}
