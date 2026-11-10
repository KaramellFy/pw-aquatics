package com.pwaquatics.shop.pricing.domain;

/**
 * One line to price: a gross unit price in cents (from the catalog, never from a client),
 * a quantity and the VAT rate in basis points (1900 = 19 %).
 */
public record PricedLine(long unitPriceCents, int quantity, int taxRateBps) {

    public PricedLine {
        if (unitPriceCents < 0 || quantity < 1 || taxRateBps < 0 || taxRateBps > 10_000) {
            throw new IllegalArgumentException("Invalid line: price, quantity or tax rate out of range");
        }
    }

    /** Gross line total; fails loudly instead of overflowing. */
    public long totalCents() {
        return Math.multiplyExact(unitPriceCents, (long) quantity);
    }
}
