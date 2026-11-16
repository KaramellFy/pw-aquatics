package com.pwaquatics.shop.pricing.domain;

import com.pwaquatics.shop.common.money.Money;
import java.util.List;

/**
 * Result of a price calculation. All amounts are gross (VAT-inclusive):
 * {@code total = subtotal − discount + shipping}, and {@code includedTax} is the VAT contained
 * in {@code total}.
 *
 * @param subtotal goods value before the discount
 * @param discount amount taken off the goods by a coupon
 * @param shipping shipping price charged (zero when a free-shipping coupon waived it)
 * @param shippingWaived shipping price a free-shipping coupon waived
 * @param lineDiscountCents the discount's share per line, in the order of the lines given
 * @param discountOutcome whether the discount took effect
 */
public record Totals(
        Money subtotal,
        Money discount,
        Money shipping,
        Money shippingWaived,
        Money total,
        Money includedTax,
        List<Long> lineDiscountCents,
        DiscountOutcome discountOutcome) {

    public Totals {
        lineDiscountCents = List.copyOf(lineDiscountCents);
    }

    /** What the coupon saved the customer: the discount on goods plus any waived shipping. */
    public Money couponSaving() {
        return Money.eur(Math.addExact(discount.amount(), shippingWaived.amount()));
    }

    /** Goods value after the discount (decides free-shipping thresholds). */
    public Money goodsAfterDiscount() {
        return Money.eur(Math.subtractExact(subtotal.amount(), discount.amount()));
    }
}
