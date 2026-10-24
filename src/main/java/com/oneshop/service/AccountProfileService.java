package com.oneshop.service;

import com.oneshop.dto.request.UpdateAccountRequest;
import com.oneshop.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;

/** A profile patch, never a detached User merge (which could restore stale security attributes). */
@Service
@Validated
public class AccountProfileService {
    private final UserRepository users;

    public AccountProfileService(UserRepository users) { this.users = users; }

    @Transactional
    @PreAuthorize("isAuthenticated() and #email == authentication.name")
    public void update(String email, @NotNull @Valid UpdateAccountRequest request) {
        String phone = request.getPhone();
        // The status predicate and the two-column write are one SQL statement under the User row write lock.
        // If Admin commits first, a disabled account cannot write; if this statement wins, Admin writes last.
        int changed = users.updateProfileFields(email, request.getFullName().trim(),
                phone == null || phone.isBlank() ? null : phone.trim(), LocalDateTime.now().withNano(0));
        if (changed != 1) throw new AccessDeniedException("Tài khoản không còn hoạt động. Vui lòng đăng nhập lại.");
    }
}
