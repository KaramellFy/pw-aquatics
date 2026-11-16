package com.pwaquatics.shop.pricing.domain;

/** What a coupon takes off. */
public enum DiscountType {
    /** A share of the goods that may be discounted, in basis points (1000 = 10 %). */
    PERCENTAGE,
    /** A fixed gross amount off the goods that may be discounted, in cents. */
    FIXED_AMOUNT,
    /** The shipping price is waived. */
    FREE_SHIPPING
}
