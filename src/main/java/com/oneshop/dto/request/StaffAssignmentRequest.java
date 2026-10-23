package com.oneshop.dto.request;

import com.oneshop.entity.ActiveStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StaffAssignmentRequest(@NotNull(message = "Vui lòng chọn nhân viên") @Positive(message = "Vui lòng chọn nhân viên") Long userId, @NotNull(message = "Vui lòng chọn chi nhánh") @Positive(message = "Vui lòng chọn chi nhánh") Long storeId,
                                     @NotNull ActiveStatus status) {}
