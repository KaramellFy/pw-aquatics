package com.pwaquatics.shop.shipping.application.view;

import com.pwaquatics.shop.common.money.Money;
import jakarta.annotation.Nullable;
import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

/**
 * A shipping method as the owner edits it (docs/API.md §3.7).
 *
 * @param dispatchDays weekdays on which parcels are handed to the carrier, Monday first
 * @param countries destination country codes, alphabetically
 */
public record AdminShippingMethodView(
        UUID id,
        String code,
        String name,
        @Nullable String description,
        @Nullable String carrier,
        Money price,
        @Nullable Money freeFrom,
        boolean liveAnimalsAllowed,
        List<DayOfWeek> dispatchDays,
        @Nullable Integer estimatedDaysMin,
        @Nullable Integer estimatedDaysMax,
        List<String> countries,
        boolean active,
        int sortOrder,
        long version) {}
