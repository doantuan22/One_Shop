package com.oneshop;

import com.oneshop.entity.Role;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.http.HttpResponse;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Login brute-force limiting and the security response headers, over real HTTP. */
class SecurityHardeningWebIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    private HttpResponse<String> apiLogin(String identifier, String password) throws Exception {
        return post("/api/auth/login", "application/json",
                "{\"email\":\"" + identifier + "\",\"password\":\"" + password + "\"}");
    }

    @Test
    void afterFiveAttemptsTheSixthIsA429WithRetryAfterWhateverThePassword() throws Exception {
        String email = "hardening.lock@oneshop.test";
        User user = new User();
        user.setEmail(email);
        user.setFullName("Lock Test");
        user.setPasswordHash(passwordEncoder.encode("Password123"));
        user.setRole(new Role(RoleName.CUSTOMER));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        withId(user);

        for (int i = 0; i < 5; i++) {
            assertThat(apiLogin(email, "wrong-" + i).statusCode()).isEqualTo(401);
        }

        HttpResponse<String> locked = apiLogin(email, "Password123"); // the right password is refused too
        assertThat(locked.statusCode()).isEqualTo(429);
        assertThat(locked.headers().firstValue("Retry-After")).hasValueSatisfying(v ->
                assertThat(Long.parseLong(v)).isBetween(1L, 900L));
        assertThat(locked.body()).contains("thử lại sau").doesNotContain("accessToken");
    }

    @Test
    void anUnknownAccountIsLimitedTheSameWayAsAKnownOne() throws Exception {
        String unknown = "hardening.nobody@oneshop.test";
        for (int i = 0; i < 5; i++) {
            assertThat(apiLogin(unknown, "x" + i).statusCode()).isEqualTo(401);
        }
        assertThat(apiLogin(unknown, "x").statusCode()).isEqualTo(429);
    }

    @Test
    void theWebLoginPageShowsTheLockInsteadOfTheGenericError() throws Exception {
        String unknown = "hardening.web@oneshop.test";
        for (int i = 0; i < 5; i++) {
            apiLogin(unknown, "x" + i);
        }
        HttpResponse<String> page = get("/login");
        String csrf = java.util.regex.Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body()) .results()
                .map(m -> m.group(1)).findFirst().orElse(null);
        String cookie = page.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN"))
                .map(c -> c.split(";")[0]).findFirst().orElse("");
        assertThat(csrf).isNotNull();

        HttpResponse<String> login = post("/login", "application/x-www-form-urlencoded",
                "identifier=" + unknown + "&password=x&_csrf=" + csrf, "Cookie", cookie);

        assertThat(login.statusCode()).isEqualTo(429);
        assertThat(login.headers().firstValue("Retry-After")).isPresent();
        assertThat(login.body()).contains("quá nhiều lần");
    }

    @Test
    void pagesCarryTheSecurityHeaders() throws Exception {
        HttpResponse<String> page = get("/login");

        String csp = page.headers().firstValue("Content-Security-Policy").orElse("");
        assertThat(csp).contains("frame-ancestors 'none'", "object-src 'none'", "base-uri 'self'",
                "form-action 'self' https://pay.payos.vn", "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net");
        assertThat(page.headers().firstValue("Referrer-Policy")).hasValue("strict-origin-when-cross-origin");
        assertThat(page.headers().firstValue("Permissions-Policy")).hasValueSatisfying(v ->
                assertThat(v).contains("camera=()", "microphone=()", "geolocation=()"));
        assertThat(page.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(page.headers().firstValue("X-Frame-Options")).hasValue("DENY");
    }
}
