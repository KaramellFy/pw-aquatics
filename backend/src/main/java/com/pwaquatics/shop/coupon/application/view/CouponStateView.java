package com.pwaquatics.shop.coupon.application.view;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.pricing.domain.DiscountType;
import jakarta.annotation.Nullable;

/**
 * The coupon in a cart or checkout quote (docs/API.md §2.4).
 *
 * @param percentage basis points of a percentage coupon (1000 = 10 %), else null
 * @param amount the amount off of a fixed-amount coupon, else null
 * @param maxDiscount cap of a percentage coupon, or null
 * @param minOrder goods value the cart must reach, or null without a minimum
 * @param applied whether it reduces the current cart
 * @param problem why it does not, as an error code ({@code COUPON_MIN_ORDER_NOT_MET},
 *     {@code COUPON_NOT_APPLICABLE}, {@code COUPON_EXPIRED}, {@code COUPON_USAGE_LIMIT_REACHED},
 *     {@code COUPON_ALREADY_USED}, {@code COUPON_INVALID}), or null
 * @param saving what it saves on this cart: the discount, or the waived shipping price (zero in
 *     the cart, where shipping is not known yet)
 */
public record CouponStateView(
        String code,
        @Nullable String description,
        DiscountType type,
        @Nullable Integer percentage,
        @Nullable Money amount,
        @Nullable Money maxDiscount,
        @Nullable Money minOrder,
        boolean applied,
        @Nullable String problem,
        Money saving) {}
