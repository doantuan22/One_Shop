package com.oneshop.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Which Store supplies what, as the checkout page shows it. A preview: it reserves nothing and is recomputed
 * (and compared through {@link #signature}) when the order is placed.
 */
public record AllocationPlanResponse(String signature, int fulfillmentCount, BigDecimal itemsTotal,
                                     List<Part> parts) {

    /** One Store's part of the cart; becomes one Fulfillment. */
    public record Part(Long storeId, String storeName, String storeAddress, BigDecimal subtotal, List<Line> lines) {
    }

    /** One SKU of a part, at the single online price. */
    public record Line(Long productId, String productName, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {
    }
}
