package com.pwaquatics.shop.catalog.application.admin.view;

import com.pwaquatics.shop.common.money.Money;
import jakarta.annotation.Nullable;
import java.util.UUID;

/** A pack size as edited in the admin area. */
public record AdminVariantView(
        UUID id,
        String sku,
        String name,
        int packSize,
        Money price,
        @Nullable Money compareAtPrice,
        boolean active,
        int sortOrder) {}
