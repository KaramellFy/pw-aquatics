package com.pwaquatics.shop.content.application.view;

import com.pwaquatics.shop.media.application.ImageView;
import java.util.UUID;
import jakarta.annotation.Nullable;

/** A published gallery photo (docs/API.md §2.3). */
public record GalleryImageView(UUID id, @Nullable String title, @Nullable String caption, ImageView image) {}
