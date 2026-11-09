package com.pwaquatics.shop.catalog.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Public catalog search criteria. Every list is optional (empty = no filter).
 *
 * @param categoryIds resolved category subtree, or {@code null} for all categories
 * @param minPriceCents compared with the product's cheapest active variant
 */
public record ProductQuery(
        String text,
        Collection<UUID> categoryIds,
        List<String> species,
        List<String> colours,
        List<String> grades,
        Long minPriceCents,
        Long maxPriceCents,
        AvailabilityFilter availability,
        boolean featuredOnly) {

    public ProductQuery {
        species = species == null ? List.of() : List.copyOf(species);
        colours = colours == null ? List.of() : List.copyOf(colours);
        grades = grades == null ? List.of() : List.copyOf(grades);
        availability = availability == null ? AvailabilityFilter.ALL : availability;
    }

    /** A filter dimension, used to leave a dimension out when counting its own facet. */
    public enum Dimension {
        SPECIES,
        COLOUR,
        GRADE,
        PRICE,
        AVAILABILITY
    }
}
