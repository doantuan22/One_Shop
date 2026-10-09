package com.oneshop.dto.request;

import com.oneshop.entity.ActiveStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StaffAssignmentRequest(@NotNull @Positive Long userId, @NotNull @Positive Long storeId,
                                     @NotNull ActiveStatus status) {}
