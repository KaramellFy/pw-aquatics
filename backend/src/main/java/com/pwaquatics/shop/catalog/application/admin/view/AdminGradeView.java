package com.pwaquatics.shop.catalog.application.admin.view;

import jakarta.annotation.Nullable;
import java.util.UUID;

/** @param rank higher means better */
public record AdminGradeView(
        UUID id,
        String name,
        String slug,
        @Nullable String scale,
        @Nullable String description,
        int rank,
        int sortOrder,
        long productCount) {}
