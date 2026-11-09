package com.pwaquatics.shop.catalog.application.view;

import jakarta.annotation.Nullable;

/** A grade; {@code rank}: higher is better within its {@code scale}. */
public record GradeView(String slug, String name, @Nullable String scale, int rank, @Nullable String description) {}
