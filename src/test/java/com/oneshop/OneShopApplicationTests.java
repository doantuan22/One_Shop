package com.oneshop;

import com.cloudinary.Cloudinary;
import com.oneshop.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OneShopApplicationTests {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private Cloudinary cloudinary;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SecurityFilterChain securityFilterChain;

    /** The context must start without a reachable SQL Server and without real credentials. */
    @Test
    void contextLoadsWithoutDatabase() {
        assertThat(jwtService).isNotNull();
        assertThat(cloudinary).isNotNull();
        assertThat(securityFilterChain).isNotNull();
    }

    @Test
    void passwordsAreHashedWithBcrypt() {
        String hash = passwordEncoder.encode("secret-password");

        assertThat(hash).startsWith("$2");
        assertThat(passwordEncoder.matches("secret-password", hash)).isTrue();
    }
}
