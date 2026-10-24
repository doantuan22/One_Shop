package com.oneshop.service.allocation;

import com.oneshop.config.CheckoutProperties;
import com.oneshop.entity.FulfillmentType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Shipping fee of ONE Order (spec OPEN-03, decided): a DELIVERY Order costs the fixed
 * {@code oneshop.checkout.shipping-fee-per-order} (30.000 VND) <b>once</b>, whatever the number of Fulfillments of
 * the Order; every independent Order of a consented COD split pays its own. A Store pickup ships nothing and is
 * always free. The fee is computed here, on the backend, with {@link BigDecimal}; the browser never supplies it.
 *
 * <p>With the fail-closed {@code UNDECIDED} switch DELIVERY has no fee and checkout refuses it.
 */
@Component
public class ShippingFeeCalculator {

    private final CheckoutProperties properties;

    public ShippingFeeCalculator(CheckoutProperties properties) {
        this.properties = properties;
    }

    /** The fee of one Order of this fulfillment type, or empty while DELIVERY is switched off. */
    public Optional<BigDecimal> feePerOrder(FulfillmentType type) {
        if (type == FulfillmentType.STORE_PICKUP) {
            return Optional.of(BigDecimal.ZERO.setScale(2));
        }
        return switch (properties.getShippingFeePolicy()) {
            case FIXED_PER_ORDER -> Optional.of(properties.getShippingFeePerOrder().setScale(2));
            case UNDECIDED -> Optional.empty();
        };
    }
}
