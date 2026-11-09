package com.pwaquatics.shop.catalog.application.view;

import com.pwaquatics.shop.common.money.Money;
import jakarta.annotation.Nullable;
import java.util.UUID;

/**
 * A purchasable pack size. {@code maxQuantity} is how many packs can be added to the cart
 * right now (capped, so exact stock is never exposed).
 */
public record VariantView(
        UUID id,
        String sku,
        String name,
        int packSize,
        Money price,
        @Nullable Money compareAtPrice,
        boolean available,
        int maxQuantity) {}
