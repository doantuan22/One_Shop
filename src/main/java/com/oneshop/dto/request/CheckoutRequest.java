package com.oneshop.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Selected cart items plus the per Store group choices. There is deliberately no unit price or total here:
 * CheckoutService recomputes both from the shared {@code Product.price} and current Store inventory (V3).
 */
public record CheckoutRequest(
        @NotEmpty(message = "Vui lòng chọn ít nhất một sản phẩm") List<@NotNull Long> cartItemIds,
        @NotEmpty(message = "Thiếu thông tin nhận hàng") List<@Valid @NotNull StoreGroupCheckoutRequest> groups) {
}
