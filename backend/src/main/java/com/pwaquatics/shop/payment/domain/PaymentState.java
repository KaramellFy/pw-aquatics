package com.pwaquatics.shop.payment.domain;

/** Where a single online payment stands at the provider. */
public enum PaymentState {
    /** Created at the provider; the customer has not paid yet. */
    CREATED,
    /** Submitted and being processed by the provider or the bank. */
    PENDING,
    SUCCEEDED,
    /** The last attempt was declined; the customer may try again. */
    FAILED,
    CANCELLED,
    REFUNDED,
    PARTIALLY_REFUNDED;

    /** Money has arrived (refunds may have followed). */
    public boolean isSettled() {
        return this == SUCCEEDED || this == REFUNDED || this == PARTIALLY_REFUNDED;
    }
}
