package com.oneshop.dto.response;

import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.RoleName;

/** No password/hash/token is exposed to the Admin view. */
public record AdminUserResponse(Long id, String email, String fullName, String phone,
                                RoleName role, ActiveStatus status) {}
