package com.pwaquatics.shop.catalog.domain;

import java.time.LocalDate;

/**
 * The editable text and settings of a product (everything except taxonomy links, variants,
 * images, stock and status). Optional values may be null.
 */
public record ProductDetails(
        String name,
        String slug,
        String shortDescription,
        String description,
        String careNotes,
        boolean liveAnimal,
        boolean featured,
        int lowStockThreshold,
        LocalDate availableFrom,
        int taxRateBps,
        String seoTitle,
        String seoDescription) {}
