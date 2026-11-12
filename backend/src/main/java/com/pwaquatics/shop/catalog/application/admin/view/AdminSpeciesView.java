package com.pwaquatics.shop.catalog.application.admin.view;

import com.pwaquatics.shop.catalog.domain.Difficulty;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.UUID;

/** A species with all care data, as edited in the admin area. */
public record AdminSpeciesView(
        UUID id,
        String scientificName,
        @Nullable String commonName,
        String slug,
        @Nullable String description,
        @Nullable String origin,
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
        @Nullable Difficulty difficulty,
        boolean tradeRestricted,
        @Nullable String restrictionNote,
        long productCount) {}
