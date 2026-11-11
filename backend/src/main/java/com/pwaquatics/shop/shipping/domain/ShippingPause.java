package com.pwaquatics.shop.shipping.domain;

import java.time.LocalDate;

/**
 * The shop setting {@code shipping.pause}: no live animals are dispatched while it is active,
 * for example during a heat wave or frost.
 *
 * @param active whether shipping of live animals is paused
 * @param message optional explanation shown to customers
 * @param until last day of the pause; without it the pause is open-ended and orders with
 *     live animals cannot be placed
 */
public record ShippingPause(boolean active, String message, LocalDate until) {

    public static final String SETTING_KEY = "shipping.pause";
    public static final ShippingPause NONE = new ShippingPause(false, null, null);

    /** Still in force on {@code today}? A pause whose last day has passed no longer counts. */
    public boolean isInForce(LocalDate today) {
        return active && (until == null || !until.isBefore(today));
    }

    public boolean isOpenEnded() {
        return active && until == null;
    }
}
