package com.pwaquatics.shop.pricing.domain;

/**
 * The terms of a coupon as the calculator needs them.
 *
 * @param value basis points for {@link DiscountType#PERCENTAGE} (1–10 000), cents for
 *     {@link DiscountType#FIXED_AMOUNT} (at least 1), 0 for {@link DiscountType#FREE_SHIPPING}
 * @param maxDiscountCents upper limit of a percentage discount, or null for none
 * @param minOrderCents goods value (before the discount, without shipping) the cart must reach
 */
public record Discount(DiscountType type, long value, Long maxDiscountCents, long minOrderCents) {

    public Discount {
        if (type == null || minOrderCents < 0 || (maxDiscountCents != null && maxDiscountCents < 1)) {
            throw new IllegalArgumentException("Invalid discount terms");
        }
        boolean valid = switch (type) {
            case PERCENTAGE -> value >= 1 && value <= 10_000;
            case FIXED_AMOUNT -> value >= 1 && maxDiscountCents == null;
            case FREE_SHIPPING -> value == 0 && maxDiscountCents == null;
        };
        if (!valid) {
            throw new IllegalArgumentException("Invalid value for a " + type + " discount");
        }
    }
}
