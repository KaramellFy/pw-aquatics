package com.pwaquatics.shop.inventory.application.view;

import com.pwaquatics.shop.inventory.domain.StockMovementReason;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.UUID;

/**
 * One entry of a product's stock history.
 *
 * @param orderId the order that caused it (placed or cancelled), if any
 * @param manual made by a person rather than by an order
 */
public record StockMovementView(
        UUID id,
        int delta,
        int stockAfter,
        StockMovementReason reason,
        @Nullable String note,
        @Nullable UUID orderId,
        boolean manual,
        Instant createdAt) {}
