package com.oneshop.dto.request;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    /** E-mail or user_name. */
    @NotBlank(message = "Vui lòng nhập email hoặc tên đăng nhập")
    private String identifier;

    @NotBlank(message = "Vui lòng nhập mật khẩu")
    private String password;

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    /**
     * Backward compatible write-only alias: older clients (API JSON and forms) still post the identifier as
     * {@code email}.
     */
    public void setEmail(String email) {
        this.identifier = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
