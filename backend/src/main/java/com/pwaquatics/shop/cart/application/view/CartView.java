package com.pwaquatics.shop.cart.application.view;

import java.util.List;

/**
 * The full cart as the server sees it now (docs/API.md §2.4).
 *
 * @param itemCount number of packs in available lines (for the header badge)
 */
public record CartView(List<CartLineView> items, CartTotalsView totals, int itemCount, List<CartWarningView> warnings) {}
