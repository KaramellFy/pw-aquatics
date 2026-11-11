package com.pwaquatics.shop.pricing.domain;

import com.pwaquatics.shop.common.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The single place where prices are added up: cart, checkout quote and order placement all
 * use it (coupons follow in a later phase). Pure functions on integer cents — no floating
 * point anywhere.
 *
 * <p>Prices are gross. The VAT contained in a gross amount {@code g} at rate {@code r} is
 * {@code g × r / (1 + r)}, rounded half-up to the cent. It is computed once per tax rate on
 * the summed amount (not per line), so rounding differences cannot accumulate.
 *
 * <p>Shipping is an ancillary service and is taxed like the goods it accompanies: with goods
 * at several tax rates, the shipping price is split in proportion to the goods value per rate.
 */
public final class PriceCalculator {

    private static final long BASIS = 10_000;

    private PriceCalculator() {}

    /** Totals without shipping (the cart). */
    public static Totals totals(List<PricedLine> lines) {
        return totals(lines, 0);
    }

    /**
     * Totals of the goods plus a gross shipping price.
     *
     * @throws IllegalArgumentException for a negative shipping price or shipping without goods
     */
    public static Totals totals(List<PricedLine> lines, long shippingCents) {
        if (shippingCents < 0 || (shippingCents > 0 && lines.isEmpty())) {
            throw new IllegalArgumentException("Shipping must not be negative and needs at least one line");
        }
        long subtotal = 0;
        TreeMap<Integer, Long> grossByRate = new TreeMap<>();
        for (PricedLine line : lines) {
            long lineTotal = line.totalCents();
            subtotal = Math.addExact(subtotal, lineTotal);
            grossByRate.merge(line.taxRateBps(), lineTotal, Math::addExact);
        }
        allocateShipping(grossByRate, subtotal, shippingCents);
        long tax = 0;
        for (Map.Entry<Integer, Long> entry : grossByRate.entrySet()) {
            tax = Math.addExact(tax, includedTaxCents(entry.getValue(), entry.getKey()));
        }
        return new Totals(
                Money.eur(subtotal),
                Money.eur(shippingCents),
                Money.eur(Math.addExact(subtotal, shippingCents)),
                Money.eur(tax));
    }

    /** VAT contained in a gross amount, rounded half-up. */
    public static long includedTaxCents(long grossCents, int taxRateBps) {
        return BigDecimal.valueOf(grossCents)
                .multiply(BigDecimal.valueOf(taxRateBps))
                .divide(BigDecimal.valueOf(BASIS + taxRateBps), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /**
     * Adds the shipping price to the per-rate amounts, in proportion to the goods value of
     * each rate. Cents that do not divide evenly go to the highest rate, so every cent of the
     * shipping price is taxed exactly once.
     */
    private static void allocateShipping(TreeMap<Integer, Long> grossByRate, long subtotal, long shippingCents) {
        if (shippingCents == 0) {
            return;
        }
        int highestRate = grossByRate.lastKey();
        long allocated = 0;
        if (subtotal > 0) {
            for (Map.Entry<Integer, Long> entry : grossByRate.entrySet()) {
                long share = BigDecimal.valueOf(shippingCents)
                        .multiply(BigDecimal.valueOf(entry.getValue()))
                        .divide(BigDecimal.valueOf(subtotal), 0, RoundingMode.DOWN)
                        .longValueExact();
                allocated += share;
                entry.setValue(Math.addExact(entry.getValue(), share));
            }
        }
        grossByRate.merge(highestRate, shippingCents - allocated, Math::addExact);
    }
}
