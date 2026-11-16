package com.pwaquatics.shop.catalog.domain;

import java.math.BigDecimal;

/**
 * The editable fields of a species. Optional values may be null.
 *
 * @param commonNameDe German texts ({@code …De}) are null where not translated
 * @param tradeRestricted the species may not be traded (e.g. on the EU list of invasive
 *     alien species); its products disappear from the shop and cannot be published
 */
public record SpeciesData(
        String scientificName,
        String commonName,
        String slug,
        String description,
        String origin,
        BigDecimal tempMinC,
        BigDecimal tempMaxC,
        BigDecimal phMin,
        BigDecimal phMax,
        Integer ghMin,
        Integer ghMax,
        Integer khMin,
        Integer khMax,
        Integer tdsMin,
        Integer tdsMax,
        Integer adultSizeMm,
        Integer lifespanMonths,
        Difficulty difficulty,
        boolean tradeRestricted,
        String restrictionNote,
        String commonNameDe,
        String descriptionDe,
        String originDe,
        String restrictionNoteDe) {

    /** The same data with another slug. */
    public SpeciesData withSlug(String newSlug) {
        return new SpeciesData(scientificName, commonName, newSlug, description, origin, tempMinC, tempMaxC, phMin,
                phMax, ghMin, ghMax, khMin, khMax, tdsMin, tdsMax, adultSizeMm, lifespanMonths, difficulty,
                tradeRestricted, restrictionNote, commonNameDe, descriptionDe, originDe, restrictionNoteDe);
    }
}
