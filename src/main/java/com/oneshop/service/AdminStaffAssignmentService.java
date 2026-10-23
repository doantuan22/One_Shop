package com.oneshop.service;

import com.oneshop.dto.request.StaffAssignmentRequest;
import com.oneshop.dto.response.StaffAssignmentResponse;
import com.oneshop.entity.*;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.mapper.StoreMapper;
import com.oneshop.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class AdminStaffAssignmentService {
    private final StaffStoreAssignmentRepository assignments;
    private final UserRepository users;
    private final StoreRepository stores;
    private final StoreMapper mapper;
    public AdminStaffAssignmentService(StaffStoreAssignmentRepository assignments, UserRepository users,
                                       StoreRepository stores, StoreMapper mapper) {
        this.assignments = assignments; this.users = users; this.stores = stores; this.mapper = mapper;
    }
    public Page<StaffAssignmentResponse> getAssignments(Long storeId, int page) {
        return assignments.searchAdmin(storeId, PageRequest.of(Math.max(0, page), 20)).map(this::response);
    }

    /** Assignment console search: Store + free text (staff name / e-mail) + assignment status. */
    public Page<StaffAssignmentResponse> searchAssignments(Long storeId, String q, ActiveStatus status, int page) {
        var like = likeOf(q);
        return assignments.searchAdmin(storeId, like, status, PageRequest.of(Math.max(0, page), 20)).map(this::response);
    }

    /** lower-case contains pattern with ! as the escape character (same rule as the other admin searches). */
    private static String likeOf(String q) {
        var text = q == null ? "" : q.trim().toLowerCase();
        if (text.isEmpty()) {
            return "";
        }
        return "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    /** Upsert the UNIQUE(user, store) pair: unassign/reactivate preserves id and assignedAt. */
    @Transactional
    public StaffAssignmentResponse assign(@NotNull @Valid StaffAssignmentRequest request) {
        User user = users.findByIdForUpdate(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên."));
        Store store = stores.findById(request.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh."));
        if (user.getRole().getName() != RoleName.STAFF) {
            throw new BadRequestException("userId", "Chỉ tài khoản STAFF được phân công chi nhánh.");
        }
        requireActive(user, store, request.status());
        var assignment = assignments.findByUserIdAndStoreId(user.getId(), store.getId()).orElseGet(() -> {
            var created = new StaffStoreAssignment(); created.setUser(user); created.setStore(store); return created;
        });
        assignment.setStatus(request.status());
        return response(assignments.saveAndFlush(assignment));
    }

    @Transactional
    public void setStatus(Long id, @NotNull ActiveStatus status, String reason) {
        var assignment = assignments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phân công."));
        User user = users.findByIdForUpdate(assignment.getUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên."));
        if (status == ActiveStatus.ACTIVE && user.getRole().getName() != RoleName.STAFF) {
            throw new BadRequestException("Chỉ tài khoản STAFF được phân công chi nhánh.");
        }
        requireActive(user, assignment.getStore(), status);
        assignment.setStatus(status);
        // the optional rationale stays display-only in the console: the assignment status itself decides the
        // Staff scope, and assignment rows have no free-text column, so nothing is written here beyond status.
        assignments.flush();
    }
    private static void requireActive(User user, Store store, ActiveStatus status) {
        if (status == ActiveStatus.ACTIVE && (!user.isActive() || store.getStatus() != ActiveStatus.ACTIVE)) {
            throw new BadRequestException("Chỉ bật phân công cho nhân viên và chi nhánh đang hoạt động.");
        }
    }
    private StaffAssignmentResponse response(StaffStoreAssignment a) {
        return new StaffAssignmentResponse(a.getId(), AdminUserService.response(a.getUser()), mapper.toResponse(a.getStore()),
                a.getStatus(), a.getAssignedAt());
    }
}
