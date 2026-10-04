package com.oneshop.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Selected cart items plus the per Store group choices. There is deliberately no unit price or total here:
 * CheckoutService recomputes both from StoreProduct.price (BR-09).
 */
public record CheckoutRequest(
        @NotEmpty(message = "Vui lòng chọn ít nhất một sản phẩm") List<@NotNull Long> cartItemIds,
        @NotEmpty(message = "Thiếu thông tin nhận hàng") List<@Valid @NotNull StoreGroupCheckoutRequest> groups) {
}
