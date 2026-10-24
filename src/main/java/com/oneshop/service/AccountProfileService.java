package com.oneshop.service;

import com.oneshop.dto.request.ChangePasswordRequest;
import com.oneshop.dto.request.UpdateAccountRequest;
import com.oneshop.exception.BadRequestException;
import com.oneshop.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;

/** A profile patch, never a detached User merge (which could restore stale security attributes). */
@Service
@Validated
public class AccountProfileService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AccountProfileService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * The customer changes their own password: the current one must match, the new one must differ from it and be typed
     * twice the same. A wrong entry is reported against its form field ({@link BadRequestException#getField()}).
     */
    @Transactional
    @PreAuthorize("isAuthenticated() and #email == authentication.name")
    public void changePassword(String email, @NotNull @Valid ChangePasswordRequest request) {
        var user = users.findByEmail(email)
                .filter(u -> u.isActive())
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không còn hoạt động. Vui lòng đăng nhập lại."));
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("currentPassword", "Mật khẩu hiện tại không đúng");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("confirmPassword", "Mật khẩu nhập lại không khớp");
        }
        if (request.getNewPassword().equals(request.getCurrentPassword())) {
            throw new BadRequestException("newPassword", "Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        int changed = users.updatePasswordHash(email, passwordEncoder.encode(request.getNewPassword()),
                LocalDateTime.now().withNano(0));
        if (changed != 1) throw new AccessDeniedException("Tài khoản không còn hoạt động. Vui lòng đăng nhập lại.");
    }

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
