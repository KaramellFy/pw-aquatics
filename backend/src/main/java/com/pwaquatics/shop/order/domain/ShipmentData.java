package com.pwaquatics.shop.order.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A parcel as the shop enters it.
 *
 * @param trackingUrl the carrier's tracking page for this number, or null if the carrier has none
 * @param shippedAt when the parcel was handed over (ignored when correcting a shipment)
 */
public record ShipmentData(String carrier, String trackingNumber, String trackingUrl, Instant shippedAt) {

    public ShipmentData {
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(trackingNumber, "trackingNumber");
    }
}
