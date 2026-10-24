package com.oneshop.entity;

/** Values of {@code promotion_usages.status}. */
public enum PromotionUsageStatus {
    /** the use is counted against the promotion's limits */
    USED,
    /** every Order of the checkout was cancelled and the use was given back */
    RELEASED
}
