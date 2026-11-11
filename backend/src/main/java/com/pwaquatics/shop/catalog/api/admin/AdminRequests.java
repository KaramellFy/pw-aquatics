package com.pwaquatics.shop.catalog.api.admin;

import com.pwaquatics.shop.catalog.domain.Difficulty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Request bodies of the taxonomy admin endpoints (docs/API.md §3.3). */
final class AdminRequests {

    private AdminRequests() {}

    /** @param slug optional; derived from the name when empty */
    record CategoryRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 120) String slug,
            UUID parentId,
            @Size(max = 5000) String description,
            @Min(0) @Max(9999) int sortOrder,
            boolean active) {}

    /** @param slug optional; derived from the scientific name when empty */
    record SpeciesRequest(
            @NotBlank @Size(max = 150) String scientificName,
            @Size(max = 150) String commonName,
            @Size(max = 170) String slug,
            @Size(max = 10000) String description,
            @Size(max = 150) String origin,
            @DecimalMin("-5.0") @DecimalMax("45.0") @Digits(integer = 3, fraction = 1) BigDecimal tempMinC,
            @DecimalMin("-5.0") @DecimalMax("45.0") @Digits(integer = 3, fraction = 1) BigDecimal tempMaxC,
            @DecimalMin("0.0") @DecimalMax("14.0") @Digits(integer = 2, fraction = 1) BigDecimal phMin,
            @DecimalMin("0.0") @DecimalMax("14.0") @Digits(integer = 2, fraction = 1) BigDecimal phMax,
            @Min(0) @Max(100) Integer ghMin,
            @Min(0) @Max(100) Integer ghMax,
            @Min(0) @Max(100) Integer khMin,
            @Min(0) @Max(100) Integer khMax,
            @Min(0) @Max(5000) Integer tdsMin,
            @Min(0) @Max(5000) Integer tdsMax,
            @Min(1) @Max(1000) Integer adultSizeMm,
            @Min(1) @Max(600) Integer lifespanMonths,
            Difficulty difficulty,
            boolean tradeRestricted,
            @Size(max = 1000) String restrictionNote) {}

    /** @param hexCode swatch colour {@code #RRGGBB}, optional */
    record ColourRequest(
            @NotBlank @Size(max = 60) String name,
            @Size(max = 80) String slug,
            @Pattern(regexp = "#[0-9A-Fa-f]{6}") String hexCode,
            @Min(0) @Max(9999) int sortOrder) {}

    /** @param rank higher means better */
    record GradeRequest(
            @NotBlank @Size(max = 60) String name,
            @Size(max = 80) String slug,
            @Size(max = 50) String scale,
            @Size(max = 1000) String description,
            @Min(0) @Max(1000) int rank,
            @Min(0) @Max(9999) int sortOrder) {}
}
