package com.pwaquatics.shop.coupon.domain;

/**
 * Why a coupon cannot be used right now. {@link #code()} is the error code the API reports
 * (docs/API.md §1.4).
 */
public enum CouponProblem {
    /** Switched off, not started yet, or unknown: the customer is not told which. */
    INVALID("COUPON_INVALID", "This code is not valid."),
    EXPIRED("COUPON_EXPIRED", "This code has expired."),
    /** Used as often as the shop allows in total. */
    USAGE_LIMIT_REACHED("COUPON_USAGE_LIMIT_REACHED", "This code has been used up."),
    /** This customer has used it as often as allowed per customer. */
    ALREADY_USED("COUPON_ALREADY_USED", "You have already used this code."),
    /** The goods are worth less than the coupon's minimum order value. */
    MIN_ORDER_NOT_MET("COUPON_MIN_ORDER_NOT_MET", "Your order does not reach the minimum value for this code yet."),
    /** Nothing in the cart may be discounted (everything is already reduced). */
    NOT_APPLICABLE("COUPON_NOT_APPLICABLE", "This code does not apply to reduced products.");

    private final String code;
    private final String message;

    CouponProblem(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    /** The problem with this error code; {@link #INVALID} for an unknown one. */
    public static CouponProblem ofCode(String code) {
        for (CouponProblem problem : values()) {
            if (problem.code.equals(code)) {
                return problem;
            }
        }
        return INVALID;
    }

    /** English text for the Problem Details {@code detail}; the frontend translates the code. */
    public String message() {
        return message;
    }
}
