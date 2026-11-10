package com.pwaquatics.shop.cart.application.view;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.media.application.ImageView;
import jakarta.annotation.Nullable;
import java.util.UUID;

/**
 * A cart line with current catalog data. For unavailable lines {@code maxQuantity} is 0
 * and the line does not count towards the totals.
 */
public record CartLineView(
        UUID id,
        UUID variantId,
        String productSlug,
        String productName,
        String variantName,
        @Nullable String speciesName,
        @Nullable String gradeName,
        String categorySlug,
        @Nullable String colourHex,
        @Nullable ImageView image,
        int quantity,
        int maxQuantity,
        Money unitPrice,
        Money lineTotal,
        boolean available) {}
