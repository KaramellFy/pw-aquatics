package com.pwaquatics.shop.admin.application.view;

import java.util.List;

/**
 * What sold in a period: best sellers and revenue by category and species, highest first.
 */
public record ProductStatsView(
        StatsPeriodView period,
        List<ProductSalesView> topProducts,
        List<GroupSalesView> byCategory,
        List<GroupSalesView> bySpecies) {}
