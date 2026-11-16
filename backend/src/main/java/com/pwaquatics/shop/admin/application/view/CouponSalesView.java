package com.pwaquatics.shop.admin.application.view;

import com.pwaquatics.shop.common.money.Money;

/** One coupon's figures in a period: orders, what it saved customers, revenue of those orders. */
public record CouponSalesView(String code, long orders, Money saving, Money revenue) {}
