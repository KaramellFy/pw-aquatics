package com.pwaquatics.shop.admin.application.view;

import jakarta.annotation.Nullable;

/**
 * A count compared with the previous period.
 *
 * @param changeBps change in basis points (+12.5 % = 1250); {@code null} when the previous
 *     value was zero
 */
public record CountKpiView(long value, long previous, @Nullable Long changeBps) {}
