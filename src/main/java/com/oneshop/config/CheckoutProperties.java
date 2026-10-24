package com.oneshop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Switches and business values of the V3 checkout ({@code oneshop.checkout.*}).
 *
 * <p>The delivery policies are APPROVED by the project owner (spec DEC-24, DEC-25): every ACTIVE Store with
 * {@code delivery_enabled} may serve any valid delivery address in Vietnam, and a DELIVERY Order costs a fixed
 * {@code 30.000 VND}, once per Order. The {@code UNDECIDED} constants stay only as a fail-closed switch: setting one
 * of them closes DELIVERY checkout.
 *
 * <p>{@code online-enabled}: ONLINE checkout (one Order, one Payment, N Fulfillments). BE-05 (timeout, reconciliation,
 * cancel/restore, refund simulation) now exists, so it is {@code true} by default; the {@code prod} profile sets it to
 * {@code false} because the mock gateway never runs there and an ONLINE Order could not be paid.
 */
@ConfigurationProperties(prefix = "oneshop.checkout")
public class CheckoutProperties {

    /** Which Stores may supply a delivery address (spec OPEN-02, decided). */
    public enum DeliveryServiceAreaPolicy {
        /** Fail-closed switch: DELIVERY checkout is refused. */
        UNDECIDED,
        /** Approved: every ACTIVE Store with {@code delivery_enabled} serves every valid address in Vietnam. */
        ALL_DELIVERY_ENABLED_STORES
    }

    /** How the shipping fee of a DELIVERY Order is decided (spec OPEN-03, decided). */
    public enum ShippingFeePolicy {
        /** Fail-closed switch: DELIVERY checkout is refused. */
        UNDECIDED,
        /** Approved: {@link #getShippingFeePerOrder()} per DELIVERY Order, whatever its number of Fulfillments. */
        FIXED_PER_ORDER
    }

    private DeliveryServiceAreaPolicy deliveryServiceAreaPolicy = DeliveryServiceAreaPolicy.ALL_DELIVERY_ENABLED_STORES;
    private ShippingFeePolicy shippingFeePolicy = ShippingFeePolicy.FIXED_PER_ORDER;
    /** The approved fee: 30.000 VND per DELIVERY Order. Pickup is always free. */
    private BigDecimal shippingFeePerOrder = new BigDecimal("30000");
    private boolean onlineEnabled = true;

    public DeliveryServiceAreaPolicy getDeliveryServiceAreaPolicy() {
        return deliveryServiceAreaPolicy;
    }

    public void setDeliveryServiceAreaPolicy(DeliveryServiceAreaPolicy deliveryServiceAreaPolicy) {
        this.deliveryServiceAreaPolicy = deliveryServiceAreaPolicy;
    }

    public ShippingFeePolicy getShippingFeePolicy() {
        return shippingFeePolicy;
    }

    public void setShippingFeePolicy(ShippingFeePolicy shippingFeePolicy) {
        this.shippingFeePolicy = shippingFeePolicy;
    }

    public BigDecimal getShippingFeePerOrder() {
        return shippingFeePerOrder;
    }

    public void setShippingFeePerOrder(BigDecimal shippingFeePerOrder) {
        if (shippingFeePerOrder == null || shippingFeePerOrder.signum() < 0) {
            throw new IllegalArgumentException("oneshop.checkout.shipping-fee-per-order must be >= 0");
        }
        this.shippingFeePerOrder = shippingFeePerOrder;
    }

    public boolean isOnlineEnabled() {
        return onlineEnabled;
    }

    public void setOnlineEnabled(boolean onlineEnabled) {
        this.onlineEnabled = onlineEnabled;
    }
}
