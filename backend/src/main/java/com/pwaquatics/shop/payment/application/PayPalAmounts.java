package com.pwaquatics.shop.payment.application;

import com.pwaquatics.shop.common.money.Money;
import java.math.BigDecimal;
import java.util.Locale;

/**
 * Amounts as PayPal writes them ({@code "17.70"} with a currency code) and as we keep them
 * (cents). Only converts the notation; nothing is calculated.
 */
public final class PayPalAmounts {

    private PayPalAmounts() {}

    /** 1770 EUR → "17.70". */
    public static String value(Money money) {
        return BigDecimal.valueOf(money.amount(), 2).toPlainString();
    }

    /**
     * "17.70", "EUR" → 1770 EUR.
     *
     * @throws IllegalArgumentException if the value is not an amount with at most two decimals
     */
    public static Money money(String value, String currency) {
        if (value == null || currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("PayPal amount without value or currency");
        }
        try {
            long cents = new BigDecimal(value.strip()).movePointRight(2).longValueExact();
            return new Money(cents, currency.strip().toUpperCase(Locale.ROOT));
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("Not a PayPal amount: " + value, e);
        }
    }
}
