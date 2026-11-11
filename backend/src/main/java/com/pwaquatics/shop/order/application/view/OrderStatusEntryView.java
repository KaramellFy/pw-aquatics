package com.pwaquatics.shop.order.application.view;

import com.pwaquatics.shop.order.domain.OrderStatus;
import jakarta.annotation.Nullable;
import java.time.Instant;

/** A step of the order's status timeline; {@code message} is an optional note for the customer. */
public record OrderStatusEntryView(OrderStatus status, Instant at, @Nullable String message) {}
