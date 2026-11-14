package com.pwaquatics.shop.shipping.application.view;

import jakarta.annotation.Nullable;
import java.time.LocalDate;

/**
 * Public, shop-wide information for every page (docs/API.md §2.3).
 *
 * @param shippingPause the live-animal shipping pause in force today, or null
 */
public record ShopInfoView(@Nullable ShippingPauseNotice shippingPause) {

    /** @param until last day of the pause; null if it is open-ended */
    public record ShippingPauseNotice(@Nullable String message, @Nullable LocalDate until) {}
}
