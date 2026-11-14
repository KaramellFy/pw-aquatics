package com.pwaquatics.shop.shipping.application.view;

import jakarta.annotation.Nullable;
import java.time.LocalDate;

/**
 * The shipping pause for live animals as stored, and whether it applies today.
 *
 * @param active switched on by the owner
 * @param inForce switched on and its last day has not passed
 * @param until last day of the pause; null for an open-ended pause (no live-animal orders at all)
 */
public record ShippingPauseView(boolean active, boolean inForce, @Nullable String message, @Nullable LocalDate until) {}
