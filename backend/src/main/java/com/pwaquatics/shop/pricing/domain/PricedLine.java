package com.pwaquatics.shop.pricing.domain;

/**
 * One line to price: a gross unit price in cents (from the catalog, never from a client),
 * a quantity, the VAT rate in basis points (1900 = 19 %) and whether a coupon may reduce it
 * (products that are already reduced are excluded).
 */
public record PricedLine(long unitPriceCents, int quantity, int taxRateBps, boolean discountable) {

    public PricedLine {
        if (unitPriceCents < 0 || quantity < 1 || taxRateBps < 0 || taxRateBps > 10_000) {
            throw new IllegalArgumentException("Invalid line: price, quantity or tax rate out of range");
        }
    }

    /** A line a coupon may reduce. */
    public PricedLine(long unitPriceCents, int quantity, int taxRateBps) {
        this(unitPriceCents, quantity, taxRateBps, true);
    }

    /** Gross line total; fails loudly instead of overflowing. */
    public long totalCents() {
        return Math.multiplyExact(unitPriceCents, (long) quantity);
    }
}
