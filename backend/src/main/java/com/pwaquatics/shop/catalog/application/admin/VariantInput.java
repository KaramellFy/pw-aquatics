package com.pwaquatics.shop.catalog.application.admin;

/**
 * A pack size as entered by the shop owner.
 *
 * @param priceCents gross price in cents
 * @param compareAtPriceCents previous price shown struck through, or null
 * @param nameDe German name of the pack size, or null
 */
public record VariantInput(
        String sku, String name, int packSize, long priceCents, Long compareAtPriceCents, boolean active,
        String nameDe) {}
