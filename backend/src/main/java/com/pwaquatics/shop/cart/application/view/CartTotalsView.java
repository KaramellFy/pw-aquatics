package com.pwaquatics.shop.cart.application.view;

import com.pwaquatics.shop.common.money.Money;

/**
 * Gross amounts: {@code total = subtotal − discount}; {@code includedTax} is the VAT contained in
 * {@code total}. Shipping (and a free-shipping coupon) is added at checkout.
 */
public record CartTotalsView(Money subtotal, Money discount, Money total, Money includedTax) {}
