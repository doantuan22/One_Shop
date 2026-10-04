package com.oneshop.dto.request;

import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Choices for one Store group of a checkout (BR-08). The combination of fulfillment type and payment method, and the
 * rule that DELIVERY needs a shipping address, are business rules checked by CheckoutService, not here.
 */
public record StoreGroupCheckoutRequest(
        @NotNull(message = "Thiếu chi nhánh") Long storeId,
        @NotNull(message = "Vui lòng chọn cách nhận hàng") FulfillmentType fulfillmentType,
        @NotNull(message = "Vui lòng chọn phương thức thanh toán") PaymentMethod paymentMethod,
        @NotBlank(message = "Vui lòng nhập tên người nhận") @Size(max = 150, message = "Tên người nhận tối đa 150 ký tự") String receiverName,
        @NotBlank(message = "Vui lòng nhập số điện thoại người nhận")
        @Pattern(regexp = "^[0-9+ ]{8,20}$", message = "Số điện thoại không hợp lệ") String receiverPhone,
        @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự") String shippingAddress) {
}
