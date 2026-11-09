package com.pwaquatics.shop.catalog.application.view;

import com.pwaquatics.shop.catalog.domain.Availability;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.media.application.ImageView;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/** A product in lists and grids (docs/API.md §2.2). Prices are server-calculated. */
public record ProductSummaryView(
        UUID id,
        String slug,
        String name,
        @Nullable SpeciesRef species,
        CategoryRef category,
        @Nullable GradeRef grade,
        List<ColourView> colours,
        @Nullable Money priceFrom,
        @Nullable Money compareAtPriceFrom,
        boolean hasMultipleVariants,
        Availability availability,
        boolean liveAnimal,
        @Nullable ImageView primaryImage,
        @Nullable ImageView secondaryImage) {}
