package com.pwaquatics.shop.pricing.domain;

import com.pwaquatics.shop.common.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The single place where prices are added up: cart, checkout quote and order placement all
 * use it. Pure functions on integer cents — no floating point anywhere.
 *
 * <p>Prices are gross. The VAT contained in a gross amount {@code g} at rate {@code r} is
 * {@code g × r / (1 + r)}, rounded half-up to the cent. It is computed once per tax rate on
 * the summed amount (not per line), so rounding differences cannot accumulate.
 *
 * <p>A coupon discount reduces the goods it may apply to (not lines that are already reduced)
 * and is split over those lines in proportion to their value, so the VAT of each rate is
 * reduced by its share. A free-shipping coupon waives the shipping price instead.
 *
 * <p>Shipping is an ancillary service and is taxed like the goods it accompanies: with goods
 * at several tax rates, the shipping price is split in proportion to the goods value per rate
 * (after the discount).
 */
public final class PriceCalculator {

    private static final long BASIS = 10_000;

    private PriceCalculator() {}

    /** Totals without shipping and discount (the cart without a coupon). */
    public static Totals totals(List<PricedLine> lines) {
        return totals(lines, 0, null);
    }

    /** Totals of the goods plus a gross shipping price, without a discount. */
    public static Totals totals(List<PricedLine> lines, long shippingCents) {
        return totals(lines, shippingCents, null);
    }

    /**
     * Totals of the goods, a gross shipping price and an optional coupon discount.
     *
     * @param discount the coupon's terms, or null without a coupon
     * @throws IllegalArgumentException for a negative shipping price or shipping without goods
     */
    public static Totals totals(List<PricedLine> lines, long shippingCents, Discount discount) {
        if (shippingCents < 0 || (shippingCents > 0 && lines.isEmpty())) {
            throw new IllegalArgumentException("Shipping must not be negative and needs at least one line");
        }
        long subtotal = 0;
        long eligible = 0;
        for (PricedLine line : lines) {
            subtotal = Math.addExact(subtotal, line.totalCents());
            if (line.discountable()) {
                eligible = Math.addExact(eligible, line.totalCents());
            }
        }

        DiscountOutcome outcome = outcome(discount, subtotal, eligible);
        long discountCents = outcome == DiscountOutcome.APPLIED ? discountCents(discount, eligible) : 0;
        List<Long> lineDiscounts = allocate(lines, discountCents, eligible);
        long shippingWaived =
                outcome == DiscountOutcome.APPLIED && discount.type() == DiscountType.FREE_SHIPPING ? shippingCents : 0;
        long shipping = shippingCents - shippingWaived;

        TreeMap<Integer, Long> grossByRate = new TreeMap<>();
        for (int i = 0; i < lines.size(); i++) {
            PricedLine line = lines.get(i);
            grossByRate.merge(line.taxRateBps(), line.totalCents() - lineDiscounts.get(i), Math::addExact);
        }
        long goods = subtotal - discountCents;
        allocateShipping(grossByRate, goods, shipping);
        long tax = 0;
        for (Map.Entry<Integer, Long> entry : grossByRate.entrySet()) {
            tax = Math.addExact(tax, includedTaxCents(entry.getValue(), entry.getKey()));
        }
        return new Totals(
                Money.eur(subtotal),
                Money.eur(discountCents),
                Money.eur(shipping),
                Money.eur(shippingWaived),
                Money.eur(Math.addExact(goods, shipping)),
                Money.eur(tax),
                lineDiscounts,
                outcome);
    }

    /** VAT contained in a gross amount, rounded half-up. */
    public static long includedTaxCents(long grossCents, int taxRateBps) {
        return BigDecimal.valueOf(grossCents)
                .multiply(BigDecimal.valueOf(taxRateBps))
                .divide(BigDecimal.valueOf(BASIS + taxRateBps), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private static DiscountOutcome outcome(Discount discount, long subtotal, long eligible) {
        if (discount == null) {
            return DiscountOutcome.NONE;
        }
        if (subtotal == 0 || subtotal < discount.minOrderCents()) {
            return DiscountOutcome.MIN_ORDER_NOT_MET;
        }
        if (discount.type() != DiscountType.FREE_SHIPPING && eligible == 0) {
            return DiscountOutcome.NOTHING_ELIGIBLE;
        }
        return DiscountOutcome.APPLIED;
    }

    /** The discount on the goods: never more than the goods it may apply to. */
    private static long discountCents(Discount discount, long eligible) {
        long amount = switch (discount.type()) {
            case PERCENTAGE -> {
                long share = BigDecimal.valueOf(eligible)
                        .multiply(BigDecimal.valueOf(discount.value()))
                        .divide(BigDecimal.valueOf(BASIS), 0, RoundingMode.HALF_UP)
                        .longValueExact();
                yield discount.maxDiscountCents() == null ? share : Math.min(share, discount.maxDiscountCents());
            }
            case FIXED_AMOUNT -> discount.value();
            case FREE_SHIPPING -> 0;
        };
        return Math.min(amount, eligible);
    }

    /**
     * Splits the discount over the discountable lines in proportion to their value (largest
     * remainder method): every line gets its rounded-down share, and the cents left over go,
     * one each, to the lines with the largest remainders (earlier lines first on a tie). The
     * shares add up to the discount exactly and never exceed a line's total.
     */
    private static List<Long> allocate(List<PricedLine> lines, long discountCents, long eligible) {
        List<Long> shares = new ArrayList<>(Collections.nCopies(lines.size(), 0L));
        if (discountCents == 0) {
            return shares;
        }
        record Remainder(int index, BigDecimal value) {}
        List<Remainder> remainders = new ArrayList<>();
        long allocated = 0;
        for (int i = 0; i < lines.size(); i++) {
            PricedLine line = lines.get(i);
            if (!line.discountable()) {
                continue;
            }
            BigDecimal[] division = BigDecimal.valueOf(discountCents)
                    .multiply(BigDecimal.valueOf(line.totalCents()))
                    .divideAndRemainder(BigDecimal.valueOf(eligible));
            long share = division[0].longValueExact();
            shares.set(i, share);
            allocated += share;
            remainders.add(new Remainder(i, division[1]));
        }
        remainders.sort(Comparator.comparing(Remainder::value).reversed().thenComparingInt(Remainder::index));
        for (int k = 0; allocated < discountCents; k++) {
            int index = remainders.get(k).index();
            shares.set(index, shares.get(index) + 1);
            allocated++;
        }
        return shares;
    }

    /**
     * Adds the shipping price to the per-rate amounts, in proportion to the goods value of
     * each rate. Cents that do not divide evenly go to the highest rate, so every cent of the
     * shipping price is taxed exactly once.
     */
    private static void allocateShipping(TreeMap<Integer, Long> grossByRate, long goods, long shippingCents) {
        if (shippingCents == 0) {
            return;
        }
        int highestRate = grossByRate.lastKey();
        long allocated = 0;
        if (goods > 0) {
            for (Map.Entry<Integer, Long> entry : grossByRate.entrySet()) {
                long share = BigDecimal.valueOf(shippingCents)
                        .multiply(BigDecimal.valueOf(entry.getValue()))
                        .divide(BigDecimal.valueOf(goods), 0, RoundingMode.DOWN)
                        .longValueExact();
                allocated += share;
                entry.setValue(Math.addExact(entry.getValue(), share));
            }
        }
        grossByRate.merge(highestRate, shippingCents - allocated, Math::addExact);
    }
}
