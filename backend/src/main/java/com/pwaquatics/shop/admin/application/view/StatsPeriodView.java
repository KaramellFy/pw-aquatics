package com.pwaquatics.shop.admin.application.view;

import com.pwaquatics.shop.admin.domain.Granularity;
import java.time.LocalDate;

/**
 * The period a statistic covers (shop time zone, both ends inclusive) and the period it is
 * compared with.
 *
 * @param granularity size of one point in the time series
 */
public record StatsPeriodView(
        LocalDate from, LocalDate to, Granularity granularity, LocalDate previousFrom, LocalDate previousTo) {}
