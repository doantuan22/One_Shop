package com.oneshop.config;

import com.oneshop.security.jwt.JwtAuthenticationFilter;
import com.oneshop.security.jwt.JwtCookieService;
import com.oneshop.security.jwt.JwtService;
import com.oneshop.security.service.CustomUserDetailsService;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Stateless security: every request is authenticated from a JWT, either from the
 * {@code Authorization: Bearer} header (API) or from an HttpOnly cookie (Thymeleaf pages).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/", "/login", "/register", "/products/**", "/health", "/error",
            "/css/**", "/js/**", "/images/**", "/vendor/**", "/favicon.ico",
            "/api/auth/**"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                                   JwtCookieService cookieService,
                                                   CustomUserDetailsService userDetailsService) throws Exception {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        // Requests that carry a Bearer token cannot be forged by a browser, so they need no CSRF token.
        RequestMatcher bearerRequest = request -> {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            return header != null && header.startsWith("Bearer ");
        };

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers(bearerRequest, paths.matcher("/api/auth/**")))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .deleteCookies(cookieService.cookieName())
                        .logoutSuccessUrl("/login?logout"))
                .authorizeHttpRequests(auth -> auth
                        // SiteMesh forwards to the decorator internally; the original request was already authorized
                        .dispatcherTypeMatchers(DispatcherType.FORWARD).permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // ...but nobody may request the decorator directly
                        .requestMatchers("/decorators/**").denyAll()
                        // Admin: chain-wide management
                        .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                        // Staff: STAFF only, because every page is scoped to the Store(s) the account is assigned
                        // to (StaffStoreScopeInterceptor). Admin works chain-wide in /admin instead.
                        .requestMatchers("/staff/**", "/api/staff/**").hasRole("STAFF")
                        // Client: the cart belongs to a customer account (USER 1--1 CART)
                        .requestMatchers("/cart/**").hasRole("CUSTOMER")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                paths.matcher("/api/**"))
                        .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"),
                                AnyRequestMatcher.INSTANCE))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, cookieService, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
