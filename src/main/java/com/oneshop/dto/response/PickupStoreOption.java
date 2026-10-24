package com.oneshop.dto.response;

/** A Store where the whole cart can be collected right now. */
public record PickupStoreOption(Long storeId, String name, String address) {
}
