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
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
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
            // catalog, Store Finder and the choice of the Store being browsed are open to guests (Roadmap V2 5)
            "/", "/login", "/register", "/products/**", "/stores/**", "/health", "/error",
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
        RequestMatcher paymentForms = paths.matcher("/orders/**");
        RequestMatcher deliveryForms = paths.matcher("/staff/orders/delivery/**");
        // Bearer API requests need no CSRF token. Payment and Staff DELIVERY forms always require it.
        RequestMatcher bearerRequest = request -> {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            // Protected form workflows require CSRF even if a Bearer header is also supplied.
            // Match parsed paths like Spring MVC, including percent-encoded segment characters.
            return header != null && header.startsWith("Bearer ") && !paymentForms.matches(request)
                    && !deliveryForms.matches(request);
        };

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        // Without an HTTP session every JWT request looks like a fresh login to Spring Security,
                        // whose default reaction is to replace the CSRF token. That replaced it on every page view,
                        // so a form on a page opened earlier (another tab, the Back button) was refused with 403.
                        // The token is still required and checked on every write, and cleared on logout.
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy())
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
                        // Client: the cart belongs to a customer account (USER 1--1 CART), and only a customer
                        // can turn it into orders
                        .requestMatchers("/cart/**", "/checkout/**", "/orders/**").hasRole("CUSTOMER")
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
