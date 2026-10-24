package com.oneshop.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request to add an item to cart. In V3, productId is the primary identifier.
 * storeProductId is retained as a backward-compatible bridge.
 */
public record AddCartItemRequest(
        Long productId,
        Long storeProductId,
        @NotNull(message = "Vui lòng nhập số lượng") @Min(value = 1, message = "Số lượng tối thiểu là 1") Integer quantity) {

    public AddCartItemRequest(Long storeProductId, Integer quantity) {
        this(null, storeProductId, quantity);
    }

    public static AddCartItemRequest forProduct(Long productId, Integer quantity) {
        return new AddCartItemRequest(productId, null, quantity);
    }
}
