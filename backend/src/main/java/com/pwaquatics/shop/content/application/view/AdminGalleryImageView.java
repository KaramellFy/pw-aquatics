package com.pwaquatics.shop.content.application.view;

import com.pwaquatics.shop.media.application.ImageView;
import java.time.Instant;
import java.util.UUID;
import jakarta.annotation.Nullable;

/** A gallery photo in the admin area, published or not (docs/API.md §3.7). */
public record AdminGalleryImageView(
        UUID id,
        @Nullable String title,
        @Nullable String caption,
        String altText,
        boolean published,
        int sortOrder,
        ImageView image,
        Instant createdAt,
        long version) {}
