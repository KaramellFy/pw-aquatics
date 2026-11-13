package com.pwaquatics.shop.order.application.view;

import com.pwaquatics.shop.order.domain.OrderStatus;
import com.pwaquatics.shop.order.domain.PaymentStatus;
import com.pwaquatics.shop.payment.application.view.PaymentInstructionsView;
import com.pwaquatics.shop.payment.domain.PaymentMethod;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A customer's order in full (docs/API.md §2.6).
 *
 * @param paymentDueAt deadline for the payment while the order is unpaid
 * @param plannedDispatchDate earliest dispatch day
 * @param paymentInstructions how to pay, while the order is waiting for a bank transfer
 * @param cancellable whether the customer may cancel the order right now
 * @param payableByCard whether the customer can (still) pay this order by card right now
 */
public record OrderDetailView(
        String orderNumber,
        OrderStatus status,
        PaymentStatus paymentStatus,
        PaymentMethod paymentMethod,
        Instant placedAt,
        @Nullable Instant paymentDueAt,
        @Nullable LocalDate plannedDispatchDate,
        List<OrderItemView> items,
        OrderTotalsView totals,
        String shippingMethodName,
        @Nullable String shippingCarrier,
        OrderAddressView shippingAddress,
        OrderAddressView billingAddress,
        @Nullable String customerNote,
        boolean containsLiveAnimals,
        List<OrderStatusEntryView> statusTimeline,
        @Nullable PaymentInstructionsView paymentInstructions,
        boolean cancellable,
        boolean payableByCard) {}
