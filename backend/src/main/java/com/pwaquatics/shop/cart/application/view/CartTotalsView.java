package com.pwaquatics.shop.cart.application.view;

import com.pwaquatics.shop.common.money.Money;

/** Gross amounts; {@code includedTax} is the VAT contained in {@code total}. Shipping is added at checkout. */
public record CartTotalsView(Money subtotal, Money total, Money includedTax) {}
