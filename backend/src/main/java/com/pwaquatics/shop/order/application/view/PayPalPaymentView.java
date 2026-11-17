package com.pwaquatics.shop.order.application.view;

import com.pwaquatics.shop.common.money.Money;
import jakarta.annotation.Nullable;
import java.time.Instant;

/**
 * What the payment page needs to show PayPal's button for an order (docs/API.md §2.7).
 *
 * @param amount the stored order total that PayPal will take
 * @param paymentDueAt until when the payment can be made; afterwards the order is cancelled
 * @param paypalOrderId the PayPal order the button opens; null once paid
 * @param clientId the shop's PayPal client id (public by design, loads PayPal's script)
 * @param alreadyPaid PayPal has taken the money or is processing it
 */
public record PayPalPaymentView(
        String orderNumber,
        Money amount,
        @Nullable Instant paymentDueAt,
        @Nullable String paypalOrderId,
        String clientId,
        boolean alreadyPaid) {}
