package com.pwaquatics.shop.catalog.application.admin.view;

import java.util.UUID;

/** A product photo; {@code sortOrder} 0 is the main photo. */
public record AdminImageView(
        UUID id, String url, String srcSet, String altText, int width, int height, int sortOrder) {}
