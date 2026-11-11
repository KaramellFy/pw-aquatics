package com.pwaquatics.shop.cart.application;

import java.util.UUID;

/** A raw cart line: which variant and how many packs. Prices are never part of a cart. */
public record CartLine(UUID variantId, int quantity) {}
