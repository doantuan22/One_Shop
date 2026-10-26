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
 * @param promotionCode   the ONE promotion code picked on the checkout page, or blank for none; it is read and decided
 *                        again by the backend (conditions, uses left, amount) and never trusted from the browser
 */
public record PlaceOrderRequest(
        @NotEmpty(message = "Vui lòng chọn ít nhất một sản phẩm") List<@NotNull Long> cartItemIds,
        @NotNull(message = "Vui lòng chọn cách nhận hàng") FulfillmentType fulfillmentType,
        @NotNull(message = "Vui lòng chọn phương thức thanh toán") PaymentMethod paymentMethod,
        @NotBlank(message = "Vui lòng nhập tên người nhận")
        @Size(max = 150, message = "Tên người nhận tối đa 150 ký tự") String receiverName,
        @NotBlank(message = "Vui lòng nhập số điện thoại người nhận")
        @Pattern(regexp = PlaceOrderRequest.RECEIVER_PHONE_REGEX, message = "Số điện thoại không hợp lệ") String receiverPhone,
        @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự") String shippingAddress,
        Long pickupStoreId,
        Boolean splitCodConsent,
        String planSignature,
        BigDecimal expectedTotal,
        @Size(max = 50, message = "Mã khuyến mãi tối đa 50 ký tự") String promotionCode) {

    /**
     * 8 to 20 characters of digits and spaces, with an optional leading {@code +}, and at least 8 digits (so a value
     * such as "++++++++" is refused).
     */
    public static final String RECEIVER_PHONE_REGEX = "^(?=(?:\\D*\\d){8})(?=.{8,20}$)\\+?[0-9 ]+$";

    /** The request without a promotion code. */
    public PlaceOrderRequest(List<Long> cartItemIds, FulfillmentType fulfillmentType, PaymentMethod paymentMethod,
                             String receiverName, String receiverPhone, String shippingAddress, Long pickupStoreId,
                             Boolean splitCodConsent, String planSignature, BigDecimal expectedTotal) {
        this(cartItemIds, fulfillmentType, paymentMethod, receiverName, receiverPhone, shippingAddress, pickupStoreId,
                splitCodConsent, planSignature, expectedTotal, null);
    }

    /** True only when the customer ticked the split consent. */
    public boolean consentsToSplit() {
        return Boolean.TRUE.equals(splitCodConsent);
    }
}
