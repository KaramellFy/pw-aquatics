package com.pwaquatics.shop.order.domain;

import com.pwaquatics.shop.common.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An internal note of the shop on an order. Never shown to the customer; never changed. */
@Entity
@Table(name = "order_notes")
public class OrderNote extends AbstractEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "author_id", updatable = false)
    private UUID authorId;

    @Column(nullable = false, length = 2000, updatable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderNote() {}

    public OrderNote(UUID orderId, UUID authorId, String body, Instant now) {
        if (body == null || body.isBlank() || body.length() > 2000) {
            throw new IllegalArgumentException("A note needs 1 to 2000 characters");
        }
        this.orderId = orderId;
        this.authorId = authorId;
        this.body = body;
        this.createdAt = now;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
