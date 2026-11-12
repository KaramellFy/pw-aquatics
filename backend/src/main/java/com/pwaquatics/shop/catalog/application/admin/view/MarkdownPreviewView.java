package com.pwaquatics.shop.catalog.application.admin.view;

import jakarta.annotation.Nullable;

/** Markdown rendered exactly as the shop will show it (sanitised HTML); null for empty input. */
public record MarkdownPreviewView(@Nullable String html) {}
