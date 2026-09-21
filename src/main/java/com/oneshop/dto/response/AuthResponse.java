package com.oneshop.dto.response;

import java.util.List;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds, String email,
                           List<String> roles) {
}
