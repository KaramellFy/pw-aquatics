package com.pwaquatics.shop.catalog.application.admin.view;

import com.pwaquatics.shop.catalog.domain.Availability;
import com.pwaquatics.shop.catalog.domain.ProductStatus;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.media.application.ImageView;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.UUID;

/**
 * A row of the admin product list (also the inventory overview).
 *
 * @param stockUnits animals in stock (exact; only admins see it)
 * @param priceFrom cheapest active pack size, null without one
 * @param image the main photo, null without photos
 */
public record AdminProductSummaryView(
        UUID id,
        String slug,
        String name,
        ProductStatus status,
        String categoryName,
        @Nullable String speciesName,
        @Nullable String gradeName,
        int stockUnits,
        int lowStockThreshold,
        Availability availability,
        @Nullable Money priceFrom,
        int variantCount,
        @Nullable ImageView image,
        Instant updatedAt) {}
