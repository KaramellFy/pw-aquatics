package com.pwaquatics.shop.catalog.application.view;

import jakarta.annotation.Nullable;
import java.util.List;

/**
 * Species profile; {@code descriptionHtml} is sanitised HTML rendered from Markdown. Texts are in
 * the request's language where written, otherwise English.
 *
 * @param languages the languages the profile has its own text in ("en", plus "de" once translated)
 */
public record SpeciesView(
        String slug,
        String scientificName,
        @Nullable String commonName,
        @Nullable String origin,
        @Nullable String descriptionHtml,
        CareView care,
        List<String> languages) {}
