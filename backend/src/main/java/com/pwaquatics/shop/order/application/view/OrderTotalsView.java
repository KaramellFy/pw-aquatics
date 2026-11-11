package com.pwaquatics.shop.order.application.view;

import com.pwaquatics.shop.common.money.Money;

/** Gross amounts: {@code total = subtotal + shipping}; {@code includedTax} is the VAT contained in {@code total}. */
public record OrderTotalsView(Money subtotal, Money shipping, Money total, Money includedTax) {}
