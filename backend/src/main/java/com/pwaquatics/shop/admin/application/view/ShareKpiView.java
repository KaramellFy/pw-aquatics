package com.pwaquatics.shop.admin.application.view;

import jakarta.annotation.Nullable;

/**
 * A share in basis points (12.5 % = 1250) compared with the previous period. Either value is
 * {@code null} when there was nothing to take a share of.
 */
public record ShareKpiView(@Nullable Integer valueBps, @Nullable Integer previousBps) {}
