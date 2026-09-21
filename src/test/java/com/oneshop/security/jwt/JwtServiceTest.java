package com.oneshop.security.jwt;

import com.oneshop.config.JwtProperties;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    private final UserDetails user = User.withUsername("a@oneshop.test").password("x").roles("CUSTOMER").build();

    private JwtService service(String secret, long minutes) {
        return new JwtService(new JwtProperties(secret, minutes, "ONESHOP_TOKEN", false));
    }

    @Test
    void generatedTokenIsValidAndCarriesTheUsername() {
        JwtService service = service(SECRET, 5);

        String token = service.generateToken(user);

        assertThat(service.extractUsername(token)).isEqualTo("a@oneshop.test");
        assertThat(service.isTokenValid(token, user)).isTrue();
        assertThat(service.getExpirationSeconds()).isEqualTo(300);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = service("another-secret-another-secret-123456", 5).generateToken(user);
        JwtService service = service(SECRET, 5);

        assertThat(service.isTokenValid(token, user)).isFalse();
        assertThatThrownBy(() -> service.extractUsername(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService service = service(SECRET, -1);
        String token = service.generateToken(user);

        assertThat(service.isTokenValid(token, user)).isFalse();
        assertThatThrownBy(() -> service.extractUsername(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenOfAnotherUserIsRejected() {
        JwtService service = service(SECRET, 5);
        UserDetails other = User.withUsername("b@oneshop.test").password("x").roles("CUSTOMER").build();

        assertThat(service.isTokenValid(service.generateToken(other), user)).isFalse();
    }

    @Test
    void missingOrWeakSecretFailsFast() {
        assertThatThrownBy(() -> service("", 5)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> service(null, 5)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service("too-short", 5)).isInstanceOf(IllegalStateException.class);
    }
}
