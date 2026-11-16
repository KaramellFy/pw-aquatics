package com.pwaquatics.shop.coupon.domain;

import com.pwaquatics.shop.common.persistence.AbstractEntity;
import com.pwaquatics.shop.pricing.domain.Discount;
import com.pwaquatics.shop.pricing.domain.DiscountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A discount code. What it takes off is calculated by the pricing module
 * ({@link #discount()}); this entity knows when it may be used.
 *
 * <p>{@code timesRedeemed} is read-only here: it changes only through conditional updates in
 * the order transaction, so an admin edit can never overwrite a redemption made meanwhile.
 */
@Entity
@Table(name = "coupons")
public class Coupon extends AbstractEntity {

    @Column(nullable = false, unique = true, length = 32, updatable = false, columnDefinition = "citext")
    private String code;

    @Column(length = 255)
    private String description;

    /** German note shown to customers, or null (the English one is shown). */
    @Column(name = "description_de", length = 255)
    private String descriptionDe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiscountType type;

    @Column(nullable = false)
    private long value;

    @Column(name = "min_order_cents", nullable = false)
    private long minOrderCents;

    @Column(name = "max_discount_cents")
    private Long maxDiscountCents;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "usage_limit_total")
    private Integer usageLimitTotal;

    @Column(name = "usage_limit_per_customer")
    private Integer usageLimitPerCustomer;

    @Column(name = "times_redeemed", nullable = false, insertable = false, updatable = false)
    private int timesRedeemed;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Coupon() {}

    /**
     * @param code a code that passed {@link CouponCode#isValid}
     */
    public static Coupon create(String code, CouponData data, UUID createdBy, Instant now) {
        if (!CouponCode.isValid(code)) {
            throw new IllegalArgumentException("Invalid coupon code");
        }
        Coupon coupon = new Coupon();
        coupon.code = code;
        coupon.createdBy = createdBy;
        coupon.createdAt = now;
        coupon.apply(data, now);
        return coupon;
    }

    public void update(CouponData data, Instant now) {
        apply(data, now);
    }

    private void apply(CouponData data, Instant now) {
        description = data.description();
        type = data.type();
        value = data.value();
        minOrderCents = data.minOrderCents();
        maxDiscountCents = data.maxDiscountCents();
        startsAt = data.startsAt();
        endsAt = data.endsAt();
        usageLimitTotal = data.usageLimitTotal();
        usageLimitPerCustomer = data.usageLimitPerCustomer();
        active = data.active();
        updatedAt = now;
    }

    /** The terms the price calculator applies. */
    public Discount discount() {
        return new Discount(type, value, maxDiscountCents, minOrderCents);
    }

    /**
     * Whether the coupon may be used at {@code now}, regardless of the cart.
     *
     * @param redemptionsByCustomer how often the customer has already used it (not cancelled), or
     *     null when the customer is not known yet (a guest)
     */
    public Optional<CouponProblem> problemAt(Instant now, Long redemptionsByCustomer) {
        return switch (statusAt(now)) {
            case INACTIVE, SCHEDULED -> Optional.of(CouponProblem.INVALID);
            case EXPIRED -> Optional.of(CouponProblem.EXPIRED);
            case USED_UP -> Optional.of(CouponProblem.USAGE_LIMIT_REACHED);
            case ACTIVE -> redemptionsByCustomer != null && usageLimitPerCustomer != null
                            && redemptionsByCustomer >= usageLimitPerCustomer
                    ? Optional.of(CouponProblem.ALREADY_USED)
                    : Optional.empty();
        };
    }

    public CouponStatus statusAt(Instant now) {
        if (!active) {
            return CouponStatus.INACTIVE;
        }
        if (endsAt != null && !now.isBefore(endsAt)) {
            return CouponStatus.EXPIRED;
        }
        if (startsAt != null && now.isBefore(startsAt)) {
            return CouponStatus.SCHEDULED;
        }
        if (usageLimitTotal != null && timesRedeemed >= usageLimitTotal) {
            return CouponStatus.USED_UP;
        }
        return CouponStatus.ACTIVE;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public String getDescriptionDe() {
        return descriptionDe;
    }

    /** Sets the German note (null where not translated). */
    public void changeGermanDescription(String descriptionDe, Instant now) {
        this.descriptionDe = descriptionDe;
        this.updatedAt = now;
    }

    public DiscountType getType() {
        return type;
    }

    public long getValue() {
        return value;
    }

    public long getMinOrderCents() {
        return minOrderCents;
    }

    public Long getMaxDiscountCents() {
        return maxDiscountCents;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public Integer getUsageLimitTotal() {
        return usageLimitTotal;
    }

    public Integer getUsageLimitPerCustomer() {
        return usageLimitPerCustomer;
    }

    public int getTimesRedeemed() {
        return timesRedeemed;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
