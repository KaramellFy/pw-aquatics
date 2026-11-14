package com.pwaquatics.shop.payment.application.view;

/**
 * What the browser needs to show Stripe's payment form for one order.
 *
 * @param clientSecret lets Stripe.js confirm this one PaymentIntent; given only to the order's owner
 * @param publishableKey the shop's public Stripe key
 * @param alreadyPaid Stripe reports the payment as done (our webhook may not have arrived yet)
 */
public record CardPaymentSession(String clientSecret, String publishableKey, boolean alreadyPaid) {}
