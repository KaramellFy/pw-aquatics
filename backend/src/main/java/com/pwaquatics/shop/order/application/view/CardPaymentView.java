package com.pwaquatics.shop.order.application.view;

import com.pwaquatics.shop.common.money.Money;
import jakarta.annotation.Nullable;
import java.time.Instant;

/**
 * What the payment page needs to show Stripe's Payment Element for an order (docs/API.md §2.7).
 *
 * @param amount the stored order total that will be charged
 * @param paymentDueAt until when the payment can be made; afterwards the order is cancelled
 * @param clientSecret the PaymentIntent's client secret for Stripe.js; null once paid
 * @param publishableKey the shop's Stripe publishable key (public by design)
 * @param alreadyPaid Stripe already has the money; the order is marked as paid when its webhook arrives
 */
public record CardPaymentView(
        String orderNumber,
        Money amount,
        @Nullable Instant paymentDueAt,
        @Nullable String clientSecret,
        String publishableKey,
        boolean alreadyPaid) {}
