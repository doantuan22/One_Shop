package com.oneshop.web;

import com.oneshop.config.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The cookie that remembers which Store a Client is browsing (selectedStoreId, Roadmap V2 6.1). The application keeps
 * no HTTP session, so a cookie is used. It only holds a Store id and gives no right to anything: the id is checked
 * against the database on every request by {@link SelectedStoreInterceptor}.
 */
@Component
public class SelectedStoreCookie {

    public static final String NAME = "ONESHOP_STORE";

    private static final Duration MAX_AGE = Duration.ofDays(30);

    private final boolean secure;

    public SelectedStoreCookie(JwtProperties jwtProperties) {
        // same transport rule as the JWT cookie: Secure wherever the site is served over HTTPS
        this.secure = jwtProperties.cookieSecure();
    }

    /** Raw cookie value, or {@code null} when the browser sent none. */
    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    public void write(HttpServletResponse response, Long storeId) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(String.valueOf(storeId), MAX_AGE));
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO));
    }

    private String cookie(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
