package com.pwaquatics.shop.coupon.application.view;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.coupon.domain.CouponStatus;
import com.pwaquatics.shop.pricing.domain.DiscountType;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A coupon in the admin area (docs/API.md §3.6).
 *
 * @param percentage basis points of a percentage coupon (1000 = 10 %), else null
 * @param amount amount off of a fixed-amount coupon, else null
 * @param startsOn first day it works (shop time), or null for "right away"
 * @param endsOn last day it works (shop time, inclusive), or null for "no end"
 * @param timesRedeemed orders that used it and were not cancelled
 * @param status where it stands right now, decided by the server
 */
public record AdminCouponView(
        UUID id,
        String code,
        @Nullable String description,
        DiscountType type,
        @Nullable Integer percentage,
        @Nullable Money amount,
        @Nullable Money maxDiscount,
        @Nullable Money minOrder,
        @Nullable LocalDate startsOn,
        @Nullable LocalDate endsOn,
        @Nullable Integer usageLimitTotal,
        @Nullable Integer usageLimitPerCustomer,
        int timesRedeemed,
        CouponStatus status,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version) {}
