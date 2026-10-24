package com.oneshop.dto.request;

import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * V3 checkout request: the chosen cart lines and ONE way to receive and pay for the whole order. There is no Store
 * for DELIVERY (the system allocates), no price, no stock and no status; the only Store a request may name is the
 * pickup Store, and the backend validates it again.
 *
 * <p>{@code planSignature} and {@code expectedTotal} are what the customer saw on the preview. They are never
 * written to the order: they only let the backend refuse to continue when the plan, the total or the consent no
 * longer match what the customer agreed to.
 *
 * @param pickupStoreId   the Store to collect at, STORE_PICKUP only
 * @param splitCodConsent explicit consent to split a COD cart into independent Orders, one per Store; a wrapper
 *                        because an unticked checkbox sends no parameter at all
 * @param planSignature   fingerprint of the allocation shown on the preview (mandatory for a COD split)
 * @param expectedTotal   total shown on the preview, to detect a price or fee change
 */
public record PlaceOrderRequest(
        @NotEmpty(message = "Vui lòng chọn ít nhất một sản phẩm") List<@NotNull Long> cartItemIds,
        @NotNull(message = "Vui lòng chọn cách nhận hàng") FulfillmentType fulfillmentType,
        @NotNull(message = "Vui lòng chọn phương thức thanh toán") PaymentMethod paymentMethod,
        @NotBlank(message = "Vui lòng nhập tên người nhận")
        @Size(max = 150, message = "Tên người nhận tối đa 150 ký tự") String receiverName,
        @NotBlank(message = "Vui lòng nhập số điện thoại người nhận")
        @Pattern(regexp = "^[0-9+ ]{8,20}$", message = "Số điện thoại không hợp lệ") String receiverPhone,
        @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự") String shippingAddress,
        Long pickupStoreId,
        Boolean splitCodConsent,
        String planSignature,
        BigDecimal expectedTotal) {

    /** True only when the customer ticked the split consent. */
    public boolean consentsToSplit() {
        return Boolean.TRUE.equals(splitCodConsent);
    }
}
