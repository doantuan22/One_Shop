package com.oneshop.dto.response;

import com.oneshop.entity.ActiveStatus;
import java.time.LocalDateTime;

public record StaffAssignmentResponse(Long id, AdminUserResponse staff, StoreResponse store,
                                      ActiveStatus status, LocalDateTime assignedAt) {}
