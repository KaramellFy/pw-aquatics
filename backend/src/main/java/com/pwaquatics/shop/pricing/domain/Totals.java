package com.pwaquatics.shop.pricing.domain;

import com.pwaquatics.shop.common.money.Money;

/**
 * Result of a price calculation. All amounts are gross (VAT-inclusive);
 * {@code includedTax} is the VAT contained in {@code total}.
 */
public record Totals(Money subtotal, Money total, Money includedTax) {}
