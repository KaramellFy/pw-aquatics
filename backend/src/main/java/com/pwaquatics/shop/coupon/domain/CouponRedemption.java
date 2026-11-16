package com.pwaquatics.shop.coupon.domain;

import com.pwaquatics.shop.common.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An order that used a coupon. Released (not deleted) when the order is cancelled, so the
 * coupon can be used again while the history stays complete.
 */
@Entity
@Table(name = "coupon_redemptions")
public class CouponRedemption extends AbstractEntity {

    @Column(name = "coupon_id", nullable = false, updatable = false)
    private UUID couponId;

    @Column(name = "order_id", nullable = false, unique = true, updatable = false)
    private UUID orderId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "discount_cents", nullable = false, updatable = false)
    private long discountCents;

    @Column(name = "redeemed_at", nullable = false, updatable = false)
    private Instant redeemedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    protected CouponRedemption() {}

    /**
     * @param discountCents what the customer saved (discount on goods or the waived shipping price)
     */
    public CouponRedemption(UUID couponId, UUID orderId, UUID userId, long discountCents, Instant now) {
        if (discountCents < 0) {
            throw new IllegalArgumentException("Negative discount");
        }
        this.couponId = couponId;
        this.orderId = orderId;
        this.userId = userId;
        this.discountCents = discountCents;
        this.redeemedAt = now;
    }

    /** @return false if it was already released */
    public boolean release(Instant now) {
        if (releasedAt != null) {
            return false;
        }
        releasedAt = now;
        return true;
    }

    public UUID getCouponId() {
        return couponId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public long getDiscountCents() {
        return discountCents;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }
}
