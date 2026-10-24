package com.oneshop.service.allocation;

import com.oneshop.config.CheckoutProperties;
import com.oneshop.entity.Store;
import org.springframework.stereotype.Component;

/**
 * Which Stores may supply a delivery address (spec OPEN-02, decided): every ACTIVE Store with
 * {@code delivery_enabled} may serve any valid delivery address in Vietnam. There is no service area: no GPS, no
 * latitude/longitude, no distance, no "nearest Store", and the province of the address is never compared with the
 * Store's. {@code stores.province_city/area} is only the address of the Store.
 *
 * <p>That a Store is ACTIVE is guaranteed by the candidate queries; this class decides the delivery part. The
 * fail-closed {@code UNDECIDED} switch makes nobody eligible.
 */
@Component
public class DeliveryServiceAreaPolicy {

    private final CheckoutProperties properties;

    public DeliveryServiceAreaPolicy(CheckoutProperties properties) {
        this.properties = properties;
    }

    /** False only while the fail-closed switch is set. */
    public boolean isDecided() {
        return properties.getDeliveryServiceAreaPolicy() != CheckoutProperties.DeliveryServiceAreaPolicy.UNDECIDED;
    }

    /** The address is not used: under the national policy it cannot make a Store ineligible. */
    public boolean canServe(Store store, String shippingAddress) {
        return switch (properties.getDeliveryServiceAreaPolicy()) {
            case ALL_DELIVERY_ENABLED_STORES -> store.isActive() && store.isDeliveryEnabled();
            case UNDECIDED -> false;
        };
    }
}
