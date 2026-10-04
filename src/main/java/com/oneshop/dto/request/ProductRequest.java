package com.oneshop.dto.request;

import com.oneshop.entity.ProductStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Admin create/update of a Product (one SKU). Products are hidden/deactivated, never deleted (BR-17). */
public record ProductRequest(
        @NotNull(message = "Vui lòng chọn danh mục") Long categoryId,
        @NotNull(message = "Vui lòng chọn thương hiệu") Long brandId,
        @NotBlank(message = "Vui lòng nhập mã SKU") @Size(max = 50, message = "SKU tối đa 50 ký tự") String sku,
        @NotBlank(message = "Vui lòng nhập tên sản phẩm") @Size(max = 255, message = "Tên tối đa 255 ký tự") String name,
        String description,
        @NotNull(message = "Vui lòng chọn trạng thái") ProductStatus status) {
}
