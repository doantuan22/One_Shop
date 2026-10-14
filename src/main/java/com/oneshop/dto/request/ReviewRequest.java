package com.oneshop.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;

public record ReviewRequest(
        Long productId,
        @NotNull(message = "Vui lòng chọn số sao") @Min(value = 1, message = "Đánh giá từ 1 đến 5 sao")
        @Max(value = 5, message = "Đánh giá từ 1 đến 5 sao") Integer rating,
        @Size(max = 1000, message = "Nhận xét tối đa 1000 ký tự") String comment,
        @NotNull(message = "Thiếu dòng hàng đã mua") @Positive(message = "Dòng hàng không hợp lệ") Long orderItemId) {

    /** Compatibility for V2 callers only. A request without an OrderItem is refused by the V3 service. */
    @Deprecated
    public ReviewRequest(Long productId, Integer rating, String comment) {
        this(productId, rating, comment, null);
    }
}
