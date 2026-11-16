package com.pwaquatics.shop.admin.application.view;

import com.pwaquatics.shop.common.money.Money;

/**
 * What a coupon has brought so far: orders that used it and count as sales, what it saved
 * those customers (discount or waived shipping) and those orders' revenue.
 */
public record CouponUsageView(long orders, Money saving, Money revenue) {}
