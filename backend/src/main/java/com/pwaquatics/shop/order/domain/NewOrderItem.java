package com.pwaquatics.shop.order.domain;

import java.util.UUID;

/**
 * A line of an order about to be placed. All values come from the catalog at the moment of
 * purchase — never from the client.
 */
public record NewOrderItem(
        UUID productId,
        UUID variantId,
        String sku,
        String productName,
        String variantName,
        String speciesName,
        String gradeName,
        boolean liveAnimal,
        int packSize,
        long unitPriceCents,
        int quantity,
        int taxRateBps) {}
