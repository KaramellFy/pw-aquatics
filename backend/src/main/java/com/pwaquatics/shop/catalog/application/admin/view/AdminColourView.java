package com.pwaquatics.shop.catalog.application.admin.view;

import jakarta.annotation.Nullable;
import java.util.UUID;

public record AdminColourView(
        UUID id, String name, String slug, @Nullable String hexCode, int sortOrder, long productCount) {}
