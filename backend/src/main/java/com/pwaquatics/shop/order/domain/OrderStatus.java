package com.pwaquatics.shop.order.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where an order is in its life (docs/ARCHITECTURE.md §5.4 "Order lifecycle").
 * Payment is tracked separately in {@link PaymentStatus}.
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    /** The statuses an order may move to from this one. Everything else is refused. */
    public Set<OrderStatus> allowedNext() {
        return switch (this) {
            case PENDING_PAYMENT -> EnumSet.of(PAID, CANCELLED);
            case PAID -> EnumSet.of(PROCESSING, CANCELLED);
            case PROCESSING -> EnumSet.of(SHIPPED, CANCELLED);
            case SHIPPED -> EnumSet.of(DELIVERED);
            case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canTransitionTo(OrderStatus next) {
        return allowedNext().contains(next);
    }

    /** Nothing more will happen to the order. */
    public boolean isFinal() {
        return allowedNext().isEmpty();
    }
}
