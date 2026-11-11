package com.pwaquatics.shop.order.application.view;

import com.pwaquatics.shop.common.money.Money;
import jakarta.annotation.Nullable;

/** A purchased line exactly as it was bought (snapshot). */
public record OrderItemView(
        String sku,
        String productName,
        String variantName,
        @Nullable String speciesName,
        @Nullable String gradeName,
        int quantity,
        Money unitPrice,
        Money lineTotal) {}
