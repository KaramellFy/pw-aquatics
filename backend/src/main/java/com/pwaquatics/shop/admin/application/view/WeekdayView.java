package com.pwaquatics.shop.admin.application.view;

import com.pwaquatics.shop.common.money.Money;

/**
 * Sales placed on one weekday (shop time zone).
 *
 * @param weekday ISO weekday, 1 = Monday … 7 = Sunday
 */
public record WeekdayView(int weekday, long orders, Money revenue) {}
