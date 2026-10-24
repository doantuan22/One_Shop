package com.oneshop.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Move a working Staff account from its current Store to another one (a Staff works at exactly one Store). */
public record StaffTransferRequest(@NotNull(message = "Vui lòng chọn nhân viên") @Positive(message = "Vui lòng chọn nhân viên") Long userId,
                                   @NotNull(message = "Vui lòng chọn chi nhánh mới") @Positive(message = "Vui lòng chọn chi nhánh mới") Long storeId) {}
