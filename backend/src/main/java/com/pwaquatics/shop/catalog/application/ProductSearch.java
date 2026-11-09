package com.pwaquatics.shop.catalog.application;

import com.pwaquatics.shop.catalog.domain.AvailabilityFilter;
import com.pwaquatics.shop.catalog.domain.ProductSort;
import java.util.List;

/** Search request as received from the API (category given by slug). */
public record ProductSearch(
        String text,
        String category,
        List<String> species,
        List<String> colours,
        List<String> grades,
        Long minPrice,
        Long maxPrice,
        AvailabilityFilter availability,
        boolean featuredOnly,
        ProductSort sort,
        int page,
        int size) {}
