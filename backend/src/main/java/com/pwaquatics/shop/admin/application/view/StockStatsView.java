package com.pwaquatics.shop.admin.application.view;

import java.util.List;

/**
 * Stock movements of a period, from the stock ledger.
 *
 * @param restocked animals added by restocks
 * @param sold animals that left stock with orders (minus those returned by cancellations)
 * @param lost animals recorded as losses
 * @param lossRate {@code lost} as a share of all animals that left stock ({@code sold + lost})
 * @param byReason net change per reason, largest change first
 */
public record StockStatsView(
        StatsPeriodView period,
        CountKpiView restocked,
        CountKpiView sold,
        CountKpiView lost,
        ShareKpiView lossRate,
        List<StockReasonView> byReason) {}
