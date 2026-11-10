package com.pwaquatics.shop.shipping.application.view;

import jakarta.annotation.Nullable;
import java.time.LocalDate;
import java.util.List;

/**
 * What can be offered for a cart and destination.
 *
 * @param methods possible shipping methods; empty if we do not deliver there or shipping is paused
 * @param paused live-animal shipping is paused right now (dispatch dates already take it into account)
 * @param pauseMessage the owner's explanation for the pause, if any
 * @param pausedUntil last day of the pause; null for an open-ended pause
 */
public record ShippingOptions(
        List<ShippingOption> methods,
        boolean paused,
        @Nullable String pauseMessage,
        @Nullable LocalDate pausedUntil) {}
