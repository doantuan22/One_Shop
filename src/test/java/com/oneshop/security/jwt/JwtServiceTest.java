package com.oneshop.security.jwt;

import com.oneshop.config.JwtProperties;
import com.oneshop.security.service.AppUserDetails;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    private final UserDetails user = account(7L, "a@oneshop.test");

    private static AppUserDetails account(Long id, String email) {
        return new AppUserDetails(id, email, "x", true, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private JwtService service(String secret, long minutes) {
        return new JwtService(new JwtProperties(secret, minutes, "ONESHOP_TOKEN", false));
    }

    @Test
    void generatedTokenIsValidAndCarriesTheUserId() {
        JwtService service = service(SECRET, 5);

        String token = service.generateToken(user);

        assertThat(service.extractUserId(token)).isEqualTo(7L);
        assertThat(service.isTokenValid(token, user)).isTrue();
        assertThat(service.getExpirationSeconds()).isEqualTo(300);
    }

    @Test
    void tokenStaysValidWhenTheEmailOrUserNameChanges() {
        JwtService service = service(SECRET, 5);

        assertThat(service.isTokenValid(service.generateToken(user), account(7L, "renamed@oneshop.test"))).isTrue();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = service("another-secret-another-secret-123456", 5).generateToken(user);
        JwtService service = service(SECRET, 5);

        assertThat(service.isTokenValid(token, user)).isFalse();
        assertThatThrownBy(() -> service.extractUserId(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService service = service(SECRET, -1);
        String token = service.generateToken(user);

        assertThat(service.isTokenValid(token, user)).isFalse();
        assertThatThrownBy(() -> service.extractUserId(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenOfAnotherUserIsRejected() {
        JwtService service = service(SECRET, 5);

        assertThat(service.isTokenValid(service.generateToken(account(8L, "b@oneshop.test")), user)).isFalse();
    }

    @Test
    void tokenIsOnlyIssuedForAnApplicationUserWithAnId() {
        JwtService service = service(SECRET, 5);
        UserDetails plain = User.withUsername("a@oneshop.test").password("x").roles("CUSTOMER").build();

        assertThatThrownBy(() -> service.generateToken(plain)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.generateToken(account(null, "a@oneshop.test")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingOrWeakSecretFailsFast() {
        assertThatThrownBy(() -> service("", 5)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> service(null, 5)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service("too-short", 5)).isInstanceOf(IllegalStateException.class);
    }
}
