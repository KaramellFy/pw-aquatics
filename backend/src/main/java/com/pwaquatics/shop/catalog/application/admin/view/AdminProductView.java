package com.pwaquatics.shop.catalog.application.admin.view;

import com.pwaquatics.shop.catalog.domain.Availability;
import com.pwaquatics.shop.catalog.domain.ProductStatus;
import com.pwaquatics.shop.catalog.domain.PublishProblem;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A product with everything the admin editor needs.
 *
 * @param version send it back with an update; a changed version means someone else saved in between
 * @param publishProblems what prevents publishing; empty when the product can go live
 */
public record AdminProductView(
        UUID id,
        long version,
        String slug,
        String name,
        ProductStatus status,
        UUID categoryId,
        @Nullable UUID speciesId,
        @Nullable UUID gradeId,
        List<UUID> colourIds,
        @Nullable String shortDescription,
        @Nullable String description,
        @Nullable String careNotes,
        boolean liveAnimal,
        boolean featured,
        int stockUnits,
        int lowStockThreshold,
        @Nullable LocalDate availableFrom,
        int taxRateBps,
        @Nullable String seoTitle,
        @Nullable String seoDescription,
        Availability availability,
        @Nullable Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        List<AdminVariantView> variants,
        List<AdminImageView> images,
        List<PublishProblem> publishProblems) {}
