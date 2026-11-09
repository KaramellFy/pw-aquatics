package com.pwaquatics.shop.catalog.application.view;

import jakarta.annotation.Nullable;

public record SpeciesRef(String slug, String scientificName, @Nullable String commonName) {}
