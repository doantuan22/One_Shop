package com.oneshop.service;

import com.oneshop.dto.request.LoginRequest;
import com.oneshop.dto.request.RegisterRequest;
import com.oneshop.dto.response.AuthResponse;
import com.oneshop.dto.response.UserResponse;

public interface AuthService {

    /**
     * @param clientIp address of the caller, used for brute-force limiting (null when unknown)
     * @throws org.springframework.security.core.AuthenticationException on bad credentials
     * @throws com.oneshop.exception.TooManyLoginAttemptsException when the account or address is temporarily locked
     */
    AuthResponse login(LoginRequest request, String clientIp);

    /** Login without a known client address: only the per-account limit applies. */
    default AuthResponse login(LoginRequest request) {
        return login(request, null);
    }

    /** @throws com.oneshop.exception.BadRequestException if the e-mail is already registered */
    UserResponse register(RegisterRequest request);
}
