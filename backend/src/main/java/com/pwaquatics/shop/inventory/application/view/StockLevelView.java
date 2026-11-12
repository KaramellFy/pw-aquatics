package com.pwaquatics.shop.inventory.application.view;

import java.util.UUID;

/** A product's stock after a change, in animals. */
public record StockLevelView(UUID productId, int stockUnits) {}
