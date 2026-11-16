package com.pwaquatics.shop.pricing.domain;

/** Whether a discount given to the calculator took effect, and if not, why. */
public enum DiscountOutcome {
    /** No discount was given. */
    NONE,
    /** The discount took effect. */
    APPLIED,
    /** The goods value is below the coupon's minimum order value. */
    MIN_ORDER_NOT_MET,
    /** None of the lines may be discounted (e.g. everything is already reduced). */
    NOTHING_ELIGIBLE
}
