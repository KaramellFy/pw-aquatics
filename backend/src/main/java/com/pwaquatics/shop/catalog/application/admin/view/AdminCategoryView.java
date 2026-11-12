package com.pwaquatics.shop.catalog.application.admin.view;

import jakarta.annotation.Nullable;
import java.util.UUID;

/** A category as the admin sees it, with the number of products in it. */
public record AdminCategoryView(
        UUID id,
        String name,
        String slug,
        @Nullable UUID parentId,
        @Nullable String description,
        int sortOrder,
        boolean active,
        long productCount) {}
