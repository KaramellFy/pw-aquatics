package com.pwaquatics.shop.payment.domain;

/**
 * How an order is paid: by bank transfer, or online through Stripe (cards, Apple Pay,
 * Google Pay) or PayPal.
 */
public enum PaymentMethod {
    BANK_TRANSFER,
    STRIPE,
    PAYPAL
}
