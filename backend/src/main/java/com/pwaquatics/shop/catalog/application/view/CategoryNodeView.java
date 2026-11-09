package com.pwaquatics.shop.catalog.application.view;

import jakarta.annotation.Nullable;
import java.util.List;

/** A category with its active sub-categories. */
public record CategoryNodeView(String slug, String name, @Nullable String description, List<CategoryNodeView> children) {}
