package com.pwaquatics.shop.admin.application.view;

import java.util.List;

/**
 * Sales statistics of a period.
 *
 * @param series one entry per bucket, oldest first, without gaps (empty buckets are zero)
 * @param byStatus all orders placed in the period, by current status, most first
 * @param byWeekday sales per weekday, Monday first, all seven days
 * @param byCountry sales per destination country, highest revenue first
 */
public record SalesStatsView(
        StatsPeriodView period,
        SalesKpisView kpis,
        List<SalesBucketView> series,
        List<StatusCountView> byStatus,
        List<WeekdayView> byWeekday,
        List<CountryView> byCountry) {}
