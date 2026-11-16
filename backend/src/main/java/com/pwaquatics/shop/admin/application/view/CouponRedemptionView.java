package com.pwaquatics.shop.admin.application.view;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.order.domain.OrderStatus;
import jakarta.annotation.Nullable;
import java.time.Instant;

/**
 * An order that used a coupon.
 *
 * @param saving what the coupon saved: the discount, or the waived shipping price
 * @param released the order was cancelled and the coupon given back
 */
public record CouponRedemptionView(
        String orderNumber,
        Instant placedAt,
        @Nullable String customerName,
        OrderStatus orderStatus,
        Money orderTotal,
        Money saving,
        boolean released) {}
