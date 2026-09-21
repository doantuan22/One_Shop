package com.oneshop.service;

import com.oneshop.dto.request.LoginRequest;
import com.oneshop.dto.request.RegisterRequest;
import com.oneshop.dto.response.AuthResponse;
import com.oneshop.dto.response.UserResponse;

public interface AuthService {

    /** @throws org.springframework.security.core.AuthenticationException on bad credentials */
    AuthResponse login(LoginRequest request);

    /** @throws com.oneshop.exception.BadRequestException if the e-mail is already registered */
    UserResponse register(RegisterRequest request);
}
