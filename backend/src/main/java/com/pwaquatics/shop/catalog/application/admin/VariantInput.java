package com.pwaquatics.shop.catalog.application.admin;

/**
 * A pack size as entered by the shop owner.
 *
 * @param priceCents gross price in cents
 * @param compareAtPriceCents previous price shown struck through, or null
 */
public record VariantInput(
        String sku, String name, int packSize, long priceCents, Long compareAtPriceCents, boolean active) {}
