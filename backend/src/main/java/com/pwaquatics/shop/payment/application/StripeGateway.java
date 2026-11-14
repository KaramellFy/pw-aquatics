package com.pwaquatics.shop.payment.application;

import com.pwaquatics.shop.common.error.ServiceUnavailableException;
import com.pwaquatics.shop.common.money.Money;
import java.util.UUID;

/**
 * The calls we make to Stripe's API. Implemented by {@code StripeApiClient}; tests replace it
 * so they never reach the network. Every method throws {@link ServiceUnavailableException}
 * ({@code PAYMENT_PROVIDER_UNAVAILABLE}) when Stripe cannot be reached or refuses the call.
 */
public interface StripeGateway {

    /**
     * A PaymentIntent as far as we need it. {@code status} is Stripe's value, e.g.
     * {@code requires_payment_method}, {@code processing}, {@code succeeded}, {@code canceled}.
     */
    record PaymentIntentData(String id, String status, String clientSecret, Money amount) {

        public boolean isSucceeded() {
            return "succeeded".equals(status);
        }

        public boolean isCanceled() {
            return "canceled".equals(status);
        }
    }

    /**
     * Creates the PaymentIntent for an order, for card payments (which include Apple Pay and
     * Google Pay). Repeating the call for the same order returns the same PaymentIntent.
     *
     * @param amount the stored order total — never an amount from a client
     */
    PaymentIntentData createPaymentIntent(UUID orderId, String orderNumber, Money amount);

    PaymentIntentData retrievePaymentIntent(String paymentIntentId);

    /**
     * Cancels a PaymentIntent that has not been paid. A PaymentIntent that has already
     * succeeded or is being processed is returned unchanged instead.
     */
    PaymentIntentData cancelPaymentIntent(String paymentIntentId);
}
