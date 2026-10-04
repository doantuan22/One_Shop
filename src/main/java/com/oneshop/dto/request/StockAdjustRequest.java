package com.oneshop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Manual stock correction by Staff. No storeId on purpose: the Store scope comes from the Staff assignment (BR-14).
 * The note is mandatory because every adjustment writes an InventoryMovement STOCK_ADJUST.
 */
public record StockAdjustRequest(
        @NotNull(message = "Vui lòng nhập số lượng tồn thực tế") @PositiveOrZero(message = "Tồn kho không được âm") Integer newQuantity,
        @NotBlank(message = "Vui lòng nhập ghi chú") @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note) {
}
