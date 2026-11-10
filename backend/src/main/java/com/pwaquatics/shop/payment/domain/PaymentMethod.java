package com.pwaquatics.shop.payment.domain;

/** How an order is paid. Card and PayPal payments are added in a later phase. */
public enum PaymentMethod {
    BANK_TRANSFER,
    STRIPE,
    PAYPAL
}
