package com.pwaquatics.shop.catalog.domain;

import java.time.LocalDate;

/**
 * The editable text and settings of a product (everything except taxonomy links, variants,
 * images, stock and status). Optional values may be null. The {@code …De} texts are the German
 * versions; null means "not translated", and the shop shows the English text instead.
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
        String seoDescription,
        String nameDe,
        String shortDescriptionDe,
        String descriptionDe,
        String careNotesDe,
        String seoTitleDe,
        String seoDescriptionDe) {

    /** The same details with another slug. */
    public ProductDetails withSlug(String newSlug) {
        return new ProductDetails(name, newSlug, shortDescription, description, careNotes, liveAnimal, featured,
                lowStockThreshold, availableFrom, taxRateBps, seoTitle, seoDescription, nameDe, shortDescriptionDe,
                descriptionDe, careNotesDe, seoTitleDe, seoDescriptionDe);
    }
}
