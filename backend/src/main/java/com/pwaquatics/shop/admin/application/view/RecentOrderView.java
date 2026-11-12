package com.pwaquatics.shop.admin.application.view;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.order.domain.OrderStatus;
import com.pwaquatics.shop.order.domain.PaymentStatus;
import jakarta.annotation.Nullable;
import java.time.Instant;

/** One of the latest orders on the dashboard. */
public record RecentOrderView(
        String orderNumber,
        Instant placedAt,
        OrderStatus status,
        PaymentStatus paymentStatus,
        Money total,
        @Nullable String customerName,
        @Nullable String countryCode,
        long animals) {}
