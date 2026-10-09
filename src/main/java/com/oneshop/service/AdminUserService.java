package com.oneshop.service;

import com.oneshop.dto.request.AdminUserRequest;
import com.oneshop.dto.response.AdminUserResponse;
import com.oneshop.entity.*;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.List;

@Service
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class AdminUserService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final StaffStoreAssignmentRepository assignments;
    private final PasswordEncoder passwords;

    public AdminUserService(UserRepository users, RoleRepository roles,
                            StaffStoreAssignmentRepository assignments, PasswordEncoder passwords) {
        this.users = users; this.roles = roles; this.assignments = assignments; this.passwords = passwords;
    }

    public Page<AdminUserResponse> getUsers(RoleName role, int page) {
        return users.searchAdmin(role, PageRequest.of(Math.max(0, page), 20)).map(AdminUserService::response);
    }

    public AdminUserResponse getUser(Long id) { return response(user(id)); }

    public List<AdminUserResponse> getStaffAccounts() {
        return users.findByRoleNameOrderByFullNameAscIdAsc(RoleName.STAFF).stream().map(AdminUserService::response).toList();
    }

    @Transactional
    public AdminUserResponse create(@NotNull @Valid AdminUserRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) throw new BadRequestException("email", "Email đã được sử dụng.");
        String password = request.password();
        if (password == null || password.isBlank() || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("password", "Mật khẩu cần ít nhất 8 ký tự và tối đa 72 byte.");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwords.encode(password));
        apply(user, request);
        return response(users.saveAndFlush(user));
    }

    @Transactional
    public AdminUserResponse update(Long id, @NotNull @Valid AdminUserRequest request) {
        User user = users.findByIdForUpdate(id).orElseThrow(AdminUserService::notFound);
        if (!user.getEmail().equals(request.email())) {
            throw new BadRequestException("email", "Email của tài khoản đã tạo không được thay đổi.");
        }
        if (request.password() != null && !request.password().isBlank()) {
            throw new BadRequestException("Không đổi mật khẩu bằng form cập nhật thông tin.");
        }
        if (user.getEmail().equals(SecurityContextHolder.getContext().getAuthentication().getName())
                && (request.role() != RoleName.ADMIN || request.status() != ActiveStatus.ACTIVE)) {
            throw new BadRequestException("Không thể tự tắt tài khoản hoặc bỏ quyền Admin đang sử dụng.");
        }
        // The same User lock serializes assignment creation and role changes. Leaving STAFF revokes rows;
        // changing back to STAFF never silently restores an earlier assignment.
        if (user.getRole().getName() == RoleName.STAFF && request.role() != RoleName.STAFF) {
            assignments.findByUserId(id).forEach(a -> a.setStatus(ActiveStatus.INACTIVE));
        }
        apply(user, request);
        users.flush();
        return response(user);
    }

    private void apply(User user, AdminUserRequest request) {
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        user.setRole(roles.findByName(request.role()).orElseThrow(() -> new BadRequestException("Role không tồn tại.")));
        user.setStatus(request.status());
    }

    private User user(Long id) { return users.findById(id).orElseThrow(AdminUserService::notFound); }
    private static ResourceNotFoundException notFound() { return new ResourceNotFoundException("Không tìm thấy người dùng."); }
    public static AdminUserResponse response(User user) {
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getRole().getName(), user.getStatus());
    }
}
