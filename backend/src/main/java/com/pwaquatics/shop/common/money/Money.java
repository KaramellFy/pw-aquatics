package com.pwaquatics.shop.common.money;

import java.util.Objects;

/**
 * An amount in minor units (cents) plus ISO 4217 currency. Gross (VAT-inclusive) unless stated.
 * Serialised as {@code {"amount": 1299, "currency": "EUR"}} (docs/API.md §1.1).
 */
public record Money(long amount, String currency) {

    public static final String EUR = "EUR";

    public Money {
        Objects.requireNonNull(currency, "currency");
        if (currency.length() != 3) {
            throw new IllegalArgumentException("Currency must be an ISO 4217 code");
        }
    }

    public static Money eur(long cents) {
        return new Money(cents, EUR);
    }
}
