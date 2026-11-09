package com.pwaquatics.shop.catalog.application.view;

import jakarta.annotation.Nullable;

public record ColourView(String slug, String name, @Nullable String hexCode) {}
