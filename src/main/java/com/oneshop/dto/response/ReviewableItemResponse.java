package com.oneshop.dto.response;

/** A Product of a COMPLETED Order and whether its Customer has already reviewed it. */
public record ReviewableItemResponse(Long productId, String productName, boolean reviewed) {
}
