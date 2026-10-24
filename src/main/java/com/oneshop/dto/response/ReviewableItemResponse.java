package com.oneshop.dto.response;

/** A received allocation line, including its verified branch and any reason it cannot be reviewed. */
public record ReviewableItemResponse(Long productId, String productName, boolean reviewed,
                                     Long orderItemId, Long orderId, Long fulfillmentId, String storeName,
                                     String unavailableReason) {
    public ReviewableItemResponse(Long productId, String productName, boolean reviewed) {
        this(productId, productName, reviewed, null, null, null, null, null);
    }

    public boolean reviewable() {
        return !reviewed && unavailableReason == null;
    }
}
