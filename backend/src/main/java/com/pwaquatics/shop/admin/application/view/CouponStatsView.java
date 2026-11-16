package com.pwaquatics.shop.admin.application.view;

import java.util.List;

/**
 * Coupon performance in a period, compared with the period before (docs/API.md §3.1).
 *
 * @param orders orders placed with a coupon that count as sales
 * @param share share of all orders that count as sales, in basis points
 * @param saving what coupons saved customers (discounts and waived shipping)
 * @param revenue revenue of the orders with a coupon
 * @param coupons per code, by revenue
 */
public record CouponStatsView(
        StatsPeriodView period,
        CountKpiView orders,
        ShareKpiView share,
        MoneyKpiView saving,
        MoneyKpiView revenue,
        List<CouponSalesView> coupons) {}
