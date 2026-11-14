package com.pwaquatics.shop.order.domain;

import com.pwaquatics.shop.common.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A parcel of an order handed to a carrier, with its tracking number. Part of the
 * {@link Order} aggregate: created and changed only through the order.
 */
@Entity
@Table(name = "shipments")
public class Shipment extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Column(nullable = false, length = 50)
    private String carrier;

    @Column(name = "tracking_number", nullable = false, length = 100)
    private String trackingNumber;

    @Column(name = "tracking_url", length = 500)
    private String trackingUrl;

    @Column(name = "shipped_at", nullable = false)
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Shipment() {}

    Shipment(Order order, ShipmentData data, UUID createdBy, Instant now) {
        this.order = order;
        apply(data, now);
        this.shippedAt = data.shippedAt();
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    /** Corrects carrier and tracking number (e.g. a typo); the shipping date stays. */
    void correct(ShipmentData data, Instant now) {
        apply(data, now);
    }

    void markDelivered(Instant at) {
        if (deliveredAt == null) {
            deliveredAt = at;
            updatedAt = at;
        }
    }

    private void apply(ShipmentData data, Instant now) {
        this.carrier = data.carrier();
        this.trackingNumber = data.trackingNumber();
        this.trackingUrl = data.trackingUrl();
        this.updatedAt = now;
    }

    public String getCarrier() {
        return carrier;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public String getTrackingUrl() {
        return trackingUrl;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }
}
