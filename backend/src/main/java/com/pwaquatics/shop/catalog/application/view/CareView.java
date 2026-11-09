package com.pwaquatics.shop.catalog.application.view;

import com.pwaquatics.shop.catalog.domain.Difficulty;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;

/** Water parameters and keeping data for a species. Every value is optional. */
public record CareView(
        @Nullable BigDecimal tempMinC,
        @Nullable BigDecimal tempMaxC,
        @Nullable BigDecimal phMin,
        @Nullable BigDecimal phMax,
        @Nullable Integer ghMin,
        @Nullable Integer ghMax,
        @Nullable Integer khMin,
        @Nullable Integer khMax,
        @Nullable Integer tdsMin,
        @Nullable Integer tdsMax,
        @Nullable Integer adultSizeMm,
        @Nullable Integer lifespanMonths,
        @Nullable Difficulty difficulty) {}
