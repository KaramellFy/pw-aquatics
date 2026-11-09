package com.pwaquatics.shop.catalog.api;

import com.pwaquatics.shop.catalog.application.ProductSearch;
import com.pwaquatics.shop.catalog.domain.AvailabilityFilter;
import com.pwaquatics.shop.catalog.domain.ProductSort;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Query parameters of {@code GET /api/v1/products} and {@code /products/facets}.
 * Multi-value filters accept repeated or comma-separated values.
 */
public record ProductSearchParams(
        @Size(max = 100) String q,
        @Size(max = 120) String category,
        @Size(max = 20) List<@Size(max = 170) String> species,
        @Size(max = 20) List<@Size(max = 80) String> colour,
        @Size(max = 20) List<@Size(max = 80) String> grade,
        @PositiveOrZero Long minPrice,
        @PositiveOrZero Long maxPrice,
        AvailabilityFilter availability,
        Boolean featured,
        ProductSort sort,
        @Min(0) @Max(10_000) Integer page,
        @Min(1) @Max(100) Integer size) {

    static final int DEFAULT_PAGE_SIZE = 24;

    ProductSearch toSearch() {
        return new ProductSearch(
                q,
                category,
                species,
                colour,
                grade,
                minPrice,
                maxPrice,
                availability,
                Boolean.TRUE.equals(featured),
                sort == null ? ProductSort.RELEVANCE : sort,
                page == null ? 0 : page,
                size == null ? DEFAULT_PAGE_SIZE : size);
    }
}
