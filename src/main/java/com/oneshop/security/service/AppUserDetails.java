package com.oneshop.security.service;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * Authenticated principal. {@link #getUsername()} stays the account e-mail (so {@code Principal#getName()}
 * and the Staff/Customer services keep working); {@link #getUserId()} is what the JWT subject carries.
 */
public class AppUserDetails extends User {

    private final Long userId;

    public AppUserDetails(Long userId, String email, String passwordHash, boolean enabled,
                          Collection<? extends GrantedAuthority> authorities) {
        super(email, passwordHash, enabled, true, true, true, authorities);
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }
}
