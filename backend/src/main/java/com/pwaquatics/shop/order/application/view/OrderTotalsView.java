package com.pwaquatics.shop.order.application.view;

import com.pwaquatics.shop.common.money.Money;

/**
 * Gross amounts: {@code total = subtotal − discount + shipping}; {@code includedTax} is the VAT
 * contained in {@code total}. {@code discount} is what a coupon took off the goods; a
 * free-shipping coupon shows as {@code shipping} zero instead.
 */
public record OrderTotalsView(Money subtotal, Money discount, Money shipping, Money total, Money includedTax) {}
