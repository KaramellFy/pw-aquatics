package com.pwaquatics.shop.coupon.domain;

/** Where a coupon stands right now, as the admin list shows it. */
public enum CouponStatus {
    ACTIVE,
    /** Starts later. */
    SCHEDULED,
    EXPIRED,
    /** Its total usage limit is reached. */
    USED_UP,
    /** Switched off by the owner. */
    INACTIVE
}
