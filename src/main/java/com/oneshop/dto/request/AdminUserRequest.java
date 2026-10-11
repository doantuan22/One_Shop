package com.oneshop.dto.request;

import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.RoleName;
import jakarta.validation.constraints.*;

/**
 * Email is immutable after creation. Password is accepted only on creation, never rendered back.
 * {@code userName} is optional (older accounts have none); when present it follows the same rule as
 * registration: 4-30 chars of letters, digits, '_' and '.', never '@'. It is trimmed on construction.
 */
public record AdminUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @Pattern(regexp = "^$|^[A-Za-z0-9_.]{4,30}$",
                message = "Tên đăng nhập 4-30 ký tự, chỉ gồm chữ, số, dấu _ và dấu chấm, không chứa @") String userName,
        @NotBlank @Size(max = 150) String fullName,
        @Pattern(regexp = "^$|^[0-9+ ]{8,20}$") String phone,
        @NotNull RoleName role,
        @NotNull ActiveStatus status,
        @Size(max = 72) String password) {

    public AdminUserRequest {
        userName = userName == null ? null : userName.trim();
    }
}
