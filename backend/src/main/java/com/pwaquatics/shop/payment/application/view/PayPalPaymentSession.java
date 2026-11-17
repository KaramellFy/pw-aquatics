package com.pwaquatics.shop.payment.application.view;

/**
 * What the browser needs to show PayPal's button for one order.
 *
 * @param paypalOrderId the PayPal order created for the stored order total; the button opens it
 * @param clientId the shop's PayPal client id (public by design, loads PayPal's script)
 * @param alreadyPaid PayPal has taken the money or is processing it
 */
public record PayPalPaymentSession(String paypalOrderId, String clientId, boolean alreadyPaid) {}
