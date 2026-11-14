package com.pwaquatics.shop.payment.application.view;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.payment.domain.PaymentMethod;
import com.pwaquatics.shop.payment.domain.PaymentState;
import jakarta.annotation.Nullable;

/**
 * An online payment of an order as the shop owner sees it (no card details exist here).
 *
 * @param reference the provider's id, e.g. Stripe's PaymentIntent {@code pi_…}
 * @param failureCode the last decline reason, e.g. {@code card_declined}
 * @param dashboardUrl the payment in the provider's dashboard (for refunds and disputes)
 */
public record PaymentSummaryView(
        PaymentMethod provider,
        String reference,
        PaymentState status,
        Money amount,
        Money refunded,
        @Nullable String methodDetail,
        @Nullable String failureCode,
        String dashboardUrl) {}
