package com.pwaquatics.shop.catalog.application.view;

import com.pwaquatics.shop.catalog.domain.Difficulty;
import jakarta.annotation.Nullable;

public record SpeciesSummaryView(String slug, String scientificName, @Nullable String commonName, @Nullable Difficulty difficulty) {}
