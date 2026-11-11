package com.pwaquatics.shop.catalog.api.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request bodies of the product admin endpoints (docs/API.md §3.2). */
final class AdminProductRequests {

    private AdminProductRequests() {}

    /**
     * @param slug optional; derived from the name when empty
     * @param taxRateBps VAT in basis points (1900 = 19 %)
     * @param version required when updating: the version the editor loaded
     */
    record ProductRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 180) String slug,
            @NotNull UUID categoryId,
            UUID speciesId,
            UUID gradeId,
            @Size(max = 30) List<@NotNull UUID> colourIds,
            @Size(max = 300) String shortDescription,
            @Size(max = 20000) String description,
            @Size(max = 10000) String careNotes,
            boolean liveAnimal,
            boolean featured,
            @Min(0) @Max(10000) int lowStockThreshold,
            LocalDate availableFrom,
            @Min(0) @Max(10000) int taxRateBps,
            @Size(max = 160) String seoTitle,
            @Size(max = 320) String seoDescription,
            @Min(0) Long version) {}

    /**
     * @param priceCents gross price in cents, set by the shop owner
     * @param compareAtPriceCents previous price shown struck through; must be higher than the price
     */
    record VariantRequest(
            @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+") String sku,
            @NotBlank @Size(max = 100) String name,
            @Min(1) @Max(1000) int packSize,
            @Min(0) @Max(10_000_000) long priceCents,
            @Min(1) @Max(10_000_000) Long compareAtPriceCents,
            boolean active) {}

    record AltTextRequest(@NotBlank @Size(max = 255) String altText) {}

    record ImageOrderRequest(@NotEmpty @Size(max = 20) List<@NotNull UUID> imageIds) {}

    record MarkdownPreviewRequest(@Size(max = 20000) String markdown) {}
}
