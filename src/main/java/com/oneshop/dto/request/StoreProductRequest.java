package com.oneshop.dto.request;

import com.oneshop.entity.ActiveStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Admin: put a SKU on sale at a Store with a price, quantity and status. */
public record StoreProductRequest(
        @NotNull(message = "Vui lòng chọn chi nhánh") Long storeId,
        @NotNull(message = "Vui lòng chọn sản phẩm") Long productId,
        @NotNull(message = "Vui lòng nhập giá") @DecimalMin(value = "0", message = "Giá không được âm")
        @Digits(integer = 16, fraction = 2, message = "Giá không hợp lệ") BigDecimal price,
        @Min(value = 0, message = "Tồn kho không được âm") int quantity,
        @NotNull(message = "Vui lòng chọn trạng thái") ActiveStatus status) {
}
