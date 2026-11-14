package com.pwaquatics.shop.shipping.domain;

import java.time.DayOfWeek;
import java.util.Set;

/**
 * Everything about a shipping method that the owner can change.
 *
 * @param priceCents gross price in cents
 * @param freeShippingThresholdCents goods value from which the method is free, or null
 * @param dispatchDays weekdays on which parcels are handed to the carrier
 * @param countries ISO 3166-1 alpha-2 codes of the destinations
 */
public record ShippingMethodData(
        String name,
        String description,
        String carrier,
        long priceCents,
        Long freeShippingThresholdCents,
        boolean liveAnimalsAllowed,
        Set<DayOfWeek> dispatchDays,
        Integer estimatedDaysMin,
        Integer estimatedDaysMax,
        Set<String> countries,
        boolean active,
        int sortOrder) {

    public ShippingMethodData {
        if (name == null || name.isBlank() || priceCents < 0
                || (freeShippingThresholdCents != null && freeShippingThresholdCents <= 0)
                || dispatchDays == null || dispatchDays.isEmpty()
                || countries == null || countries.isEmpty()
                || (estimatedDaysMin != null && estimatedDaysMin < 0)) {
            throw new IllegalArgumentException("Incomplete or inconsistent shipping method");
        }
        dispatchDays = Set.copyOf(dispatchDays);
        countries = Set.copyOf(countries);
    }
}
