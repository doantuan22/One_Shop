package com.oneshop.service.allocation;

/** Why a cart could not be allocated. No Order, stock change or Payment is made for any of them. */
public enum AllocationFailure {
    NO_DEMAND,
    /** The eligible Stores together do not have the requested quantity of at least one SKU. */
    OUT_OF_STOCK,
    /** The chosen pickup Store cannot supply the whole cart. */
    INSUFFICIENT_AT_PICKUP_STORE,
    /** No Store is eligible for this fulfillment type. */
    NO_ELIGIBLE_STORE
}
