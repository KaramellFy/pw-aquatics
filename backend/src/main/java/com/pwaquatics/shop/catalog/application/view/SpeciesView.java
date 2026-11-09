package com.pwaquatics.shop.catalog.application.view;

import jakarta.annotation.Nullable;

/** Species profile; {@code descriptionHtml} is sanitised HTML rendered from Markdown. */
public record SpeciesView(
        String slug, String scientificName, @Nullable String commonName, @Nullable String origin, @Nullable String descriptionHtml, CareView care) {}
