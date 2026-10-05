package com.oneshop.security;

import com.oneshop.dto.response.StoreResponse;
import com.oneshop.service.StoreService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.security.Principal;
import java.util.List;

/**
 * Establishes the Store scope of every request to the Staff area ({@code /staff/**}, {@code /api/staff/**}) before
 * its controller runs (Roadmap V2 BR-14, section 5.1).
 *
 * <p>The scope is the list of Stores the authenticated Staff has an ACTIVE assignment to, read from the database on
 * each request and exposed as the request attribute {@link #ASSIGNED_STORES}. It is never taken from a request
 * parameter, header or the JWT. A Staff without a valid assignment only reaches the landing page, which tells them so;
 * every other Staff URL answers 403.
 *
 * <p>Role checks stay in {@code SecurityConfig}; Services still call {@code StoreService.requireAssignedStore} for the
 * Store of the data they touch.
 */
@Component
public class StaffStoreScopeInterceptor implements HandlerInterceptor {

    /** Request attribute holding the {@code List<StoreResponse>} scope; also read by the Staff layout. */
    public static final String ASSIGNED_STORES = "assignedStores";

    private static final String LANDING_PAGE = "/staff";

    private final StoreService storeService;

    public StaffStoreScopeInterceptor(StoreService storeService) {
        this.storeService = storeService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        Principal principal = request.getUserPrincipal();
        List<StoreResponse> stores = principal == null
                ? List.of()
                : storeService.getAssignedStores(principal.getName());
        request.setAttribute(ASSIGNED_STORES, stores);

        if (stores.isEmpty() && !isLandingPage(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Tài khoản chưa được phân công chi nhánh");
            return false;
        }
        return true;
    }

    private static boolean isLandingPage(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals(LANDING_PAGE) || path.equals(LANDING_PAGE + "/");
    }
}
