package com.oneshop.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** The client sends only the StoreProduct and the quantity; price is always read from the database (BR-09). */
public record AddCartItemRequest(
        @NotNull(message = "Thiếu sản phẩm tại chi nhánh") Long storeProductId,
        @NotNull(message = "Vui lòng nhập số lượng") @Min(value = 1, message = "Số lượng tối thiểu là 1") Integer quantity) {
}
