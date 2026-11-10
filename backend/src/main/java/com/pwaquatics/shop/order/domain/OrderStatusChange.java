package com.pwaquatics.shop.order.domain;

import com.pwaquatics.shop.common.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One entry of an order's history: which status it moved to, when, and who did it. */
@Entity
@Table(name = "order_status_history")
public class OrderStatusChange extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20, updatable = false)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20, updatable = false)
    private OrderStatus toStatus;

    /** Internal note, never shown to the customer. */
    @Column(length = 1000, updatable = false)
    private String note;

    @Column(name = "customer_message", length = 1000, updatable = false)
    private String customerMessage;

    /** The user who made the change; null for the system (e.g. the expiry job). */
    @Column(name = "changed_by", updatable = false)
    private UUID changedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderStatusChange() {}

    OrderStatusChange(
            Order order,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String note,
            String customerMessage,
            UUID changedBy,
            Instant now) {
        this.order = order;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.note = note;
        this.customerMessage = customerMessage;
        this.changedBy = changedBy;
        this.createdAt = now;
    }

    public OrderStatus getToStatus() {
        return toStatus;
    }

    public String getCustomerMessage() {
        return customerMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
