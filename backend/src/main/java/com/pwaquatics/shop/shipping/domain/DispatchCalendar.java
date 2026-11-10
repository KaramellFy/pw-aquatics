package com.pwaquatics.shop.shipping.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * Works out on which day a parcel can leave. Pure date arithmetic; the caller supplies
 * "today" in the shop's time zone.
 *
 * <p>Public holidays are not taken into account yet; the owner can bridge them with a
 * shipping pause.
 */
public final class DispatchCalendar {

    private DispatchCalendar() {}

    /**
     * The first dispatch weekday on or after {@code earliest}.
     *
     * @param earliest first day the parcel could be packed
     * @param dispatchDays weekdays on which parcels are handed to the carrier (not empty)
     */
    public static LocalDate nextDispatchDate(LocalDate earliest, Set<DayOfWeek> dispatchDays) {
        if (dispatchDays.isEmpty()) {
            throw new IllegalArgumentException("At least one dispatch weekday is required");
        }
        LocalDate date = earliest;
        while (!dispatchDays.contains(date.getDayOfWeek())) {
            date = date.plusDays(1);
        }
        return date;
    }

    /**
     * The earliest dispatch date for an order placed on {@code today}.
     *
     * @param handlingDays days needed before a parcel can leave (0 = same day)
     * @param pausedUntil last day of a shipping pause that applies to this parcel, or null
     */
    public static LocalDate plannedDispatchDate(
            LocalDate today, int handlingDays, Set<DayOfWeek> dispatchDays, LocalDate pausedUntil) {
        LocalDate earliest = today.plusDays(handlingDays);
        if (pausedUntil != null && !pausedUntil.isBefore(earliest)) {
            earliest = pausedUntil.plusDays(1);
        }
        return nextDispatchDate(earliest, dispatchDays);
    }
}
