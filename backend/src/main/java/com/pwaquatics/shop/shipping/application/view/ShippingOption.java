package com.pwaquatics.shop.shipping.application.view;

import com.pwaquatics.shop.common.money.Money;
import jakarta.annotation.Nullable;
import java.time.LocalDate;

/**
 * A shipping method as offered for one cart and destination.
 *
 * @param price gross price for this cart (zero when the free-shipping threshold is reached)
 * @param freeFrom goods value from which this method is free, if it has such a threshold
 * @param plannedDispatchDate earliest day the parcel can leave (payment must have arrived by then)
 * @param estimatedDeliveryFrom earliest expected delivery day, if the method states a transit time
 */
public record ShippingOption(
        String code,
        String name,
        @Nullable String description,
        @Nullable String carrier,
        Money price,
        @Nullable Money freeFrom,
        LocalDate plannedDispatchDate,
        @Nullable LocalDate estimatedDeliveryFrom,
        @Nullable LocalDate estimatedDeliveryTo) {}
