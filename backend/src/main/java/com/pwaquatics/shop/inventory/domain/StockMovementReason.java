package com.pwaquatics.shop.inventory.domain;

/** Why the stock of a product changed (matches the CHECK constraint on {@code stock_movements.reason}). */
public enum StockMovementReason {
    INITIAL,
    RESTOCK,
    ORDER_PLACED,
    ORDER_CANCELLED,
    MANUAL_ADJUSTMENT,
    LOSS,
    RETURN
}
