package com.pwaquatics.shop.catalog.application.view;

import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.List;

/**
 * A catalog page for the sitemap.
 *
 * @param languages the languages the page has its own text in ("en", plus "de" once translated)
 * @param lastModified when its content last changed, where the catalog records it
 */
public record SitemapEntry(String slug, List<String> languages, @Nullable Instant lastModified) {}
