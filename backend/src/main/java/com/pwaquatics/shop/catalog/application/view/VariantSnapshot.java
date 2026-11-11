package com.pwaquatics.shop.catalog.application.view;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.media.application.ImageView;
import jakarta.annotation.Nullable;
import java.util.UUID;

/**
 * The current, authoritative state of a variant for other modules (cart, checkout):
 * price, tax rate and how many packs can be bought right now.
 *
 * @param liveAnimal live animals need a suitable shipping method and dispatch day
 * @param purchasable the product is visible in the shop, the variant is active and at least one pack is in stock
 * @param maxQuantity packs that can be bought now (0 if not purchasable); capped per line
 * @param stockUnits animals in stock, shared by all pack sizes of the product (internal; never sent to clients)
 */
public record VariantSnapshot(
        UUID variantId,
        UUID productId,
        String productSlug,
        String productName,
        String variantName,
        String sku,
        int packSize,
        Money unitPrice,
        int taxRateBps,
        boolean liveAnimal,
        boolean purchasable,
        int maxQuantity,
        int stockUnits,
        @Nullable String speciesName,
        @Nullable String gradeName,
        String categorySlug,
        @Nullable String colourHex,
        @Nullable ImageView image) {}
