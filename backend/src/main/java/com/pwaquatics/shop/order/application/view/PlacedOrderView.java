package com.pwaquatics.shop.order.application.view;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.order.domain.OrderStatus;
import com.pwaquatics.shop.order.domain.PaymentStatus;
import com.pwaquatics.shop.payment.application.view.PaymentInstructionsView;
import jakarta.annotation.Nullable;
import java.time.Instant;

/** The answer to a placed order: its number and how to pay it. */
public record PlacedOrderView(
        String orderNumber,
        OrderStatus status,
        PaymentStatus paymentStatus,
        Money total,
        @Nullable Instant paymentDueAt,
        @Nullable PaymentInstructionsView paymentInstructions) {}
