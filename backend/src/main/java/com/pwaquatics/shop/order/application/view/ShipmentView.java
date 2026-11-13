package com.pwaquatics.shop.order.application.view;

import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.UUID;

/**
 * A parcel of an order.
 *
 * @param trackingUrl the carrier's tracking page for this parcel, if the carrier has one
 */
public record ShipmentView(
        UUID id,
        String carrier,
        String trackingNumber,
        @Nullable String trackingUrl,
        Instant shippedAt,
        @Nullable Instant deliveredAt) {}
