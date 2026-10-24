package com.oneshop.service;

import com.oneshop.dto.request.StaffAssignmentRequest;
import com.oneshop.dto.request.StaffTransferRequest;
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
        return assignments.searchAdmin(storeId, com.oneshop.service.query.Paging.page(page, 20)).map(this::response);
    }

    /** Assignment console search: Store + free text (staff name / e-mail) + assignment status. */
    public Page<StaffAssignmentResponse> searchAssignments(Long storeId, String q, ActiveStatus status, int page) {
        var words = com.oneshop.service.search.TextSearch.words(q);
        if (!words.isEmpty()) {
            var matched = assignments.searchAdmin(storeId, "", status, org.springframework.data.domain.Pageable.unpaged())
                    .getContent().stream()
                    .filter(a -> com.oneshop.service.search.TextSearch.matchesAll(words, a.getUser().getFullName(), a.getUser().getEmail(),
                            a.getUser().getUserName(), a.getStore().getName()))
                    .toList();
            return com.oneshop.service.search.TextSearch.slice(matched, com.oneshop.service.query.Paging.page(page, 20)).map(this::response);
        }
        var like = likeOf(q);
        return assignments.searchAdmin(storeId, like, status, com.oneshop.service.query.Paging.page(page, 20)).map(this::response);
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
        if (request.status() == ActiveStatus.ACTIVE) {
            requireNoOtherWorkplace(user, store.getId(), "userId");
        }
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
        if (status == ActiveStatus.ACTIVE) {
            requireNoOtherWorkplace(user, assignment.getStore().getId(), null);
        }
        assignment.setStatus(status);
        // the optional rationale stays display-only in the console: the assignment status itself decides the
        // Staff scope, and assignment rows have no free-text column, so nothing is written here beyond status.
        assignments.flush();
    }
    /**
     * Move a working Staff from the current Store to another one in a single step: the current assignment is
     * suspended and the new one is opened, so the Staff never works at two Stores (nor at none in between).
     */
    @Transactional
    public StaffAssignmentResponse transfer(@NotNull @Valid StaffTransferRequest request) {
        User user = users.findByIdForUpdate(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên."));
        Store target = stores.findById(request.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh."));
        if (user.getRole().getName() != RoleName.STAFF) {
            throw new BadRequestException("userId", "Chỉ tài khoản STAFF được chuyển chi nhánh.");
        }
        requireActive(user, target, ActiveStatus.ACTIVE);
        var working = assignments.findByUserIdAndStatus(user.getId(), ActiveStatus.ACTIVE);
        if (working.isEmpty()) {
            throw new BadRequestException("userId",
                    "Nhân viên chưa làm việc tại chi nhánh nào. Dùng \"Gán nhân viên\" hoặc \"Khôi phục\".");
        }
        if (working.size() == 1 && working.get(0).getStore().getId().equals(target.getId())) {
            throw new BadRequestException("storeId", "Nhân viên đang làm việc tại chi nhánh này.");
        }
        // the Staff moves: the old workplace is removed (it is not a suspension, so it must not stay behind as a
        // "suspended" row that could be restored), and any old row for the target Store is replaced by a fresh one
        assignments.deleteAll(working);
        assignments.findByUserIdAndStoreId(user.getId(), target.getId()).ifPresent(assignments::delete);
        assignments.flush();
        var created = new StaffStoreAssignment();
        created.setUser(user);
        created.setStore(target);
        created.setStatus(ActiveStatus.ACTIVE);
        return response(assignments.saveAndFlush(created));
    }

    /** A Staff works at exactly one Store: another ACTIVE assignment must be moved, not duplicated. */
    private void requireNoOtherWorkplace(User user, Long storeId, String field) {
        assignments.findByUserIdAndStatus(user.getId(), ActiveStatus.ACTIVE).stream()
                .filter(a -> !a.getStore().getId().equals(storeId))
                .findFirst()
                .ifPresent(a -> {
                    String message = "Nhân viên đang làm việc tại " + a.getStore().getName()
                            + ". Mỗi nhân viên chỉ làm việc tại một chi nhánh: dùng \"Chuyển chi nhánh\" để đổi nơi làm việc.";
                    throw field == null ? new BadRequestException(message) : new BadRequestException(field, message);
                });
    }

    /** Staff accounts that currently work at a Store (they can only be transferred, not assigned again). */
    public java.util.Set<Long> getWorkingStaffIds() {
        return new java.util.HashSet<>(assignments.findWorkingStaffIds());
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
