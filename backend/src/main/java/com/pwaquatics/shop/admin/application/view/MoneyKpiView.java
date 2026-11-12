package com.pwaquatics.shop.admin.application.view;

import com.pwaquatics.shop.common.money.Money;
import jakarta.annotation.Nullable;

/**
 * An amount compared with the previous period.
 *
 * @param changeBps change in basis points; {@code null} when the previous amount was zero
 */
public record MoneyKpiView(Money value, Money previous, @Nullable Long changeBps) {}
