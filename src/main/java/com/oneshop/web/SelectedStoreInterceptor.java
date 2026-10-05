package com.oneshop.web;

import com.oneshop.dto.response.StoreResponse;
import com.oneshop.service.StoreService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * Resolves the Store context of every Client page (BR-03). The id in the {@link SelectedStoreCookie} is looked up in
 * the database on each request; only an existing ACTIVE Store becomes the request attribute {@link #SELECTED_STORE}
 * (a {@link StoreResponse}), which controllers and the Client layout read.
 *
 * <p>No attribute means "whole chain". A cookie that is not a number, points to a Store that does not exist or to one
 * that has become INACTIVE is dropped, so the Client quietly falls back to the whole chain.
 */
@Component
public class SelectedStoreInterceptor implements HandlerInterceptor {

    public static final String SELECTED_STORE = "selectedStore";

    private final SelectedStoreCookie cookie;
    private final StoreService storeService;

    public SelectedStoreInterceptor(SelectedStoreCookie cookie, StoreService storeService) {
        this.cookie = cookie;
        this.storeService = storeService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String raw = cookie.read(request);
        if (raw == null) {
            return true;
        }
        Optional<StoreResponse> store = storeService.findSelectableStore(parse(raw));
        if (store.isPresent()) {
            request.setAttribute(SELECTED_STORE, store.get());
        } else {
            cookie.clear(response);
        }
        return true;
    }

    private static Long parse(String raw) {
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
