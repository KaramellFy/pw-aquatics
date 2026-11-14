package com.pwaquatics.shop.order.application.view;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.order.domain.OrderStatus;
import com.pwaquatics.shop.order.domain.PaymentStatus;
import com.pwaquatics.shop.payment.domain.PaymentMethod;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One row of the admin order list.
 *
 * @param customerName the name on the billing address
 * @param countryCode the delivery country
 * @param itemCount number of items ordered (a pack counts as one)
 */
public record AdminOrderSummaryView(
        String orderNumber,
        Instant placedAt,
        OrderStatus status,
        PaymentStatus paymentStatus,
        PaymentMethod paymentMethod,
        Money total,
        String customerName,
        String customerEmail,
        String countryCode,
        int itemCount,
        boolean containsLiveAnimals,
        @Nullable LocalDate plannedDispatchDate,
        @Nullable Instant paymentDueAt) {}
