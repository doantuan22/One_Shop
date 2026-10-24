package com.oneshop.dto.response;

import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.PaymentMethod;

import com.oneshop.service.promotion.PromotionService;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the checkout page shows before the customer confirms. Everything is recalculated, and compared with
 * {@code plan.signature} / {@code grandTotal}, when the order is placed; a preview reserves nothing.
 *
 * @param selection           the chosen lines (flat; there is no grouping by Store)
 * @param receiverName        suggestion from the default address or profile; the customer may change it
 * @param receiverPhone       suggestion, see above
 * @param shippingAddress     suggestion from the default address, or {@code null}
 * @param fulfillmentType     the way to receive the order that this preview was computed for
 * @param paymentMethod       the payment method this preview was computed for
 * @param pickupStoreId       the pickup Store this preview was computed for, or {@code null}
 * @param onlineEnabled       false while ONLINE checkout is closed (switch off, or production without a payment gateway)
 * @param deliveryAvailable   false while the delivery service-area or shipping-fee policy is not approved
 * @param deliveryBlockedReason why DELIVERY is closed, or {@code null}
 * @param pickupStores        Stores where the whole cart can be collected
 * @param plan                the allocation for this choice, or {@code null} when none is possible
 * @param planProblem         why there is no plan, or {@code null}
 * @param shippingFeePerOrder fee of one Order, or {@code null} when the owner has not decided it
 * @param orderCount          how many Orders confirming will create (1, or one per Store for a COD split)
 * @param grandTotal          sum of the totals of those Orders, or {@code null} without a plan
 * @param splitConsentRequired the customer must explicitly accept the split before ordering
 * @param paymentTimeoutMinutes minutes to pay an ONLINE Order from creation
 * @param appliedPromotion    the promotion code applied to this preview, or {@code null}
 * @param promotionProblem    why the requested code cannot be used, or {@code null}
 * @param promotions          the codes the customer may pick (each marked eligible or not for this cart)
 * @param discountTotal       discount taken off the goods of the whole confirmation (0 without a promotion); the
 *                            {@code grandTotal} already has it taken off
 */
public record CheckoutPreviewResponse(CartResponse selection, String receiverName, String receiverPhone,
                                      String shippingAddress, FulfillmentType fulfillmentType,
                                      PaymentMethod paymentMethod, Long pickupStoreId, boolean onlineEnabled,
                                      boolean deliveryAvailable, String deliveryBlockedReason,
                                      List<PickupStoreOption> pickupStores, AllocationPlanResponse plan,
                                      String planProblem, BigDecimal shippingFeePerOrder, int orderCount,
                                      BigDecimal grandTotal, boolean splitConsentRequired,
                                      int paymentTimeoutMinutes, PromotionService.Applied appliedPromotion,
                                      String promotionProblem, List<PromotionService.Option> promotions,
                                      BigDecimal discountTotal) {

    /** The shape without promotions (no code applied, none offered). */
    public CheckoutPreviewResponse(CartResponse selection, String receiverName, String receiverPhone,
                                   String shippingAddress, FulfillmentType fulfillmentType,
                                   PaymentMethod paymentMethod, Long pickupStoreId, boolean onlineEnabled,
                                   boolean deliveryAvailable, String deliveryBlockedReason,
                                   List<PickupStoreOption> pickupStores, AllocationPlanResponse plan,
                                   String planProblem, BigDecimal shippingFeePerOrder, int orderCount,
                                   BigDecimal grandTotal, boolean splitConsentRequired, int paymentTimeoutMinutes) {
        this(selection, receiverName, receiverPhone, shippingAddress, fulfillmentType, paymentMethod, pickupStoreId,
                onlineEnabled, deliveryAvailable, deliveryBlockedReason, pickupStores, plan, planProblem,
                shippingFeePerOrder, orderCount, grandTotal, splitConsentRequired, paymentTimeoutMinutes, null, null,
                List.of(), BigDecimal.ZERO);
    }

    public boolean hasDiscount() {
        return discountTotal != null && discountTotal.signum() > 0;
    }

    /** All shipping fees of this confirmation: one per Order that will be created (or {@code null} if not decided). */
    public BigDecimal shippingFeeTotal() {
        return shippingFeePerOrder == null ? null : shippingFeePerOrder.multiply(BigDecimal.valueOf(orderCount));
    }

    /** Legacy shape (cart selection and receiver suggestions only), kept for V2 callers and tests. */
    public CheckoutPreviewResponse(CartResponse selection, String receiverName, String receiverPhone,
                                   String shippingAddress) {
        this(selection, receiverName, receiverPhone, shippingAddress, FulfillmentType.DELIVERY, PaymentMethod.COD,
                null, false, false, null, List.of(), null, null, null, 0, null, false, 15);
    }
}
