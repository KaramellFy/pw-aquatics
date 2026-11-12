package com.pwaquatics.shop.admin.application.view;

import com.pwaquatics.shop.common.money.Money;
import jakarta.annotation.Nullable;
import java.util.UUID;

/**
 * A best seller.
 *
 * @param productId {@code null} for lines whose product no longer exists
 * @param gradeName tells apart products with the same name (e.g. "Blue Dream" High and Premium)
 * @param units animals (or items) sold: packs × pack size
 * @param packs packs sold
 * @param revenue line totals, without shipping
 */
public record ProductSalesView(
        @Nullable UUID productId, String name, @Nullable String gradeName, long units, long packs, Money revenue) {}
