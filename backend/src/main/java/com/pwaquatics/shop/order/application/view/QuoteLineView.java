package com.pwaquatics.shop.order.application.view;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.media.application.ImageView;
import jakarta.annotation.Nullable;
import java.util.UUID;

/** A cart line as it would be ordered right now, with current catalog data. */
public record QuoteLineView(
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
        Money unitPrice,
        Money lineTotal) {}
