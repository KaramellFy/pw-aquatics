package com.pwaquatics.shop.payment.domain;

/** Where a refund the shop started stands at the provider. */
public enum PaymentRefundState {
    /** The provider accepted it but has not finished it yet (counts as refunded until it fails). */
    PENDING,
    SUCCEEDED,
    /** The provider gave up after accepting it; the money stayed with the shop. */
    FAILED
}
