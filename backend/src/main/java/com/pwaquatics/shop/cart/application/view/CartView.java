package com.pwaquatics.shop.cart.application.view;

import com.pwaquatics.shop.coupon.application.view.CouponStateView;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * The full cart as the server sees it now (docs/API.md §2.4).
 *
 * @param itemCount number of packs in available lines (for the header badge)
 * @param coupon the coupon the visitor entered and whether it reduces the cart, or null
 */
public record CartView(
        List<CartLineView> items,
        CartTotalsView totals,
        int itemCount,
        List<CartWarningView> warnings,
        @Nullable CouponStateView coupon) {}
