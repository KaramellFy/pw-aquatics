package com.pwaquatics.shop.cart.application.view;

import jakarta.annotation.Nullable;
import java.util.UUID;

/**
 * Something the server changed or noticed while recalculating the cart.
 *
 * @param code {@code QUANTITY_REDUCED} (less stock than requested; quantity was lowered to
 *     {@code quantity}), {@code ITEM_UNAVAILABLE} (cannot be bought right now) or
 *     {@code ITEM_REMOVED} (the product no longer exists)
 */
public record CartWarningView(
        String code, @Nullable UUID itemId, @Nullable String productName, @Nullable Integer quantity) {}
