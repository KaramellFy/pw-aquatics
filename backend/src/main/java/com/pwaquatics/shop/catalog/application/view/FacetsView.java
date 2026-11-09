package com.pwaquatics.shop.catalog.application.view;

import com.pwaquatics.shop.common.money.Money;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * Filter options with product counts for the current search. Each dimension is counted
 * without its own filter, so other options in the same group stay selectable.
 */
public record FacetsView(
        List<FacetOption> species,
        List<FacetOption> colours,
        List<FacetOption> grades,
        AvailabilityCounts availability,
        @Nullable PriceRange price) {

    /** {@code hexCode} is only set for colours. */
    public record FacetOption(String slug, String name, @Nullable String hexCode, long count) {}

    public record AvailabilityCounts(long inStock, long comingSoon) {}

    /** Lowest and highest "from" price; null when nothing matches. */
    public record PriceRange(Money min, Money max) {}
}
