package com.oneshop.entity;

/** Values of {@code promotions.discount_type}. */
public enum PromotionType {
    /** {@code discount_value} percent of the goods, at most {@code max_discount_amount} when that is set */
    PERCENT,
    /** {@code discount_value} VND off the goods, never more than the goods */
    FIXED
}
