package com.pwaquatics.shop.admin.application.view;

import com.pwaquatics.shop.common.money.Money;
import jakarta.annotation.Nullable;

/**
 * Sales of one category or species.
 *
 * @param name {@code null} for products without a species (or category no longer known)
 * @param units animals (or items) sold
 * @param revenue line totals, without shipping
 */
public record GroupSalesView(@Nullable String name, long units, Money revenue) {}
