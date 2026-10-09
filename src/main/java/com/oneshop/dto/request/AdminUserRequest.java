package com.oneshop.dto.request;

import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.RoleName;
import jakarta.validation.constraints.*;

/** Email is immutable after creation. Password is accepted only on creation, never rendered back. */
public record AdminUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 150) String fullName,
        @Pattern(regexp = "^$|^[0-9+ ]{8,20}$") String phone,
        @NotNull RoleName role,
        @NotNull ActiveStatus status,
        @Size(max = 72) String password) {}
