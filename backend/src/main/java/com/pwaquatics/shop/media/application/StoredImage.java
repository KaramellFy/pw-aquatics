package com.pwaquatics.shop.media.application;

/** An uploaded image after processing: where its renditions are and its size in pixels. */
public record StoredImage(String storageKey, int width, int height) {}
