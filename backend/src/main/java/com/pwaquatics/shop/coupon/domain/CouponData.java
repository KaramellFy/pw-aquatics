package com.pwaquatics.shop.coupon.domain;

import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.pricing.domain.Discount;
import com.pwaquatics.shop.pricing.domain.DiscountType;
import java.time.Instant;

/**
 * Everything about a coupon the owner can change (the code is fixed once created).
 *
 * @param value basis points for a percentage, cents for a fixed amount, 0 for free shipping
 * @param maxDiscountCents cap for a percentage discount, or null
 * @param startsAt first moment the coupon works, or null for "right away"
 * @param endsAt first moment it no longer works (exclusive), or null for "no end"
 * @throws BusinessRuleException {@code COUPON_VALUE_INVALID} for a value or cap that does not fit
 *     the type; {@code RANGE_INVALID} when the end is not after the start
 */
public record CouponData(
        String description,
        DiscountType type,
        long value,
        long minOrderCents,
        Long maxDiscountCents,
        Instant startsAt,
        Instant endsAt,
        Integer usageLimitTotal,
        Integer usageLimitPerCustomer,
        boolean active) {

    public CouponData {
        if (type == null || minOrderCents < 0
                || (usageLimitTotal != null && usageLimitTotal < 1)
                || (usageLimitPerCustomer != null && usageLimitPerCustomer < 1)) {
            throw new IllegalArgumentException("Incomplete coupon");
        }
        try {
            new Discount(type, value, maxDiscountCents, minOrderCents);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("COUPON_VALUE_INVALID",
                    "Percentages run from 0.01 to 100, fixed amounts start at 0.01, and only a percentage can have a cap.");
        }
        if (startsAt != null && endsAt != null && !startsAt.isBefore(endsAt)) {
            throw new BusinessRuleException("RANGE_INVALID", "The last day must not be before the first day.");
        }
        description = description == null || description.isBlank() ? null : description.strip();
    }
}
