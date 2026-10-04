package com.oneshop.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewRequest(
        @NotNull(message = "Thiếu sản phẩm") Long productId,
        @NotNull(message = "Vui lòng chọn số sao") @Min(value = 1, message = "Đánh giá từ 1 đến 5 sao")
        @Max(value = 5, message = "Đánh giá từ 1 đến 5 sao") Integer rating,
        @Size(max = 1000, message = "Nhận xét tối đa 1000 ký tự") String comment) {
}
