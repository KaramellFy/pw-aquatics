package com.pwaquatics.shop.pricing.domain;

import com.pwaquatics.shop.common.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The single place where prices are added up (cart now; shipping, coupons and orders in
 * later phases). Pure functions on integer cents — no floating point anywhere.
 *
 * <p>Prices are gross. The VAT contained in a gross amount {@code g} at rate {@code r} is
 * {@code g × r / (1 + r)}, rounded half-up to the cent. It is computed once per tax rate on
 * the summed amount (not per line), so rounding differences cannot accumulate.
 */
public final class PriceCalculator {

    private static final long BASIS = 10_000;

    private PriceCalculator() {}

    public static Totals totals(List<PricedLine> lines) {
        long subtotal = 0;
        Map<Integer, Long> grossByRate = new TreeMap<>();
        for (PricedLine line : lines) {
            long lineTotal = line.totalCents();
            subtotal = Math.addExact(subtotal, lineTotal);
            grossByRate.merge(line.taxRateBps(), lineTotal, Math::addExact);
        }
        long tax = 0;
        for (Map.Entry<Integer, Long> entry : grossByRate.entrySet()) {
            tax = Math.addExact(tax, includedTaxCents(entry.getValue(), entry.getKey()));
        }
        // No shipping or discounts yet: the total equals the subtotal.
        return new Totals(Money.eur(subtotal), Money.eur(subtotal), Money.eur(tax));
    }

    /** VAT contained in a gross amount, rounded half-up. */
    public static long includedTaxCents(long grossCents, int taxRateBps) {
        return BigDecimal.valueOf(grossCents)
                .multiply(BigDecimal.valueOf(taxRateBps))
                .divide(BigDecimal.valueOf(BASIS + taxRateBps), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
