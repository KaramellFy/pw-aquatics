package com.pwaquatics.shop.inventory.domain;

import com.pwaquatics.shop.common.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One entry of the append-only stock ledger: how many animals were added or removed, why,
 * and the balance afterwards. Entries are never changed or deleted.
 */
@Entity
@Table(name = "stock_movements")
public class StockMovement extends AbstractEntity {

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(nullable = false, updatable = false)
    private int delta;

    @Column(name = "stock_after", nullable = false, updatable = false)
    private int stockAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private StockMovementReason reason;

    @Column(name = "order_id", updatable = false)
    private UUID orderId;

    @Column(length = 500, updatable = false)
    private String note;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockMovement() {}

    /** A movement caused by an order (placed: negative delta; cancelled: positive delta). */
    public static StockMovement forOrder(
            UUID productId, int delta, int stockAfter, StockMovementReason reason, UUID orderId, Instant now) {
        if (delta == 0) {
            throw new IllegalArgumentException("A stock movement must change the stock");
        }
        StockMovement movement = new StockMovement();
        movement.productId = productId;
        movement.delta = delta;
        movement.stockAfter = stockAfter;
        movement.reason = reason;
        movement.orderId = orderId;
        movement.createdAt = now;
        return movement;
    }

    public UUID getProductId() {
        return productId;
    }

    public int getDelta() {
        return delta;
    }

    public int getStockAfter() {
        return stockAfter;
    }

    public StockMovementReason getReason() {
        return reason;
    }

    public UUID getOrderId() {
        return orderId;
    }
}
