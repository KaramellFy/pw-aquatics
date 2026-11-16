package com.pwaquatics.shop.cart.application;

import java.util.List;
import java.util.UUID;

/**
 * The customer's cart as the checkout reads it.
 *
 * @param lines variant ids and quantities in the order they were added
 * @param couponId the coupon the customer entered, or null
 */
public record CheckoutCart(List<CartLine> lines, UUID couponId) {

    public CheckoutCart {
        lines = List.copyOf(lines);
    }
}
