package com.pwaquatics.shop.admin.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Rounding rules for the figures on the dashboard, kept in one place so they stay consistent. */
public final class Ratios {

    private Ratios() {}

    /**
     * Share of {@code part} in {@code whole} in basis points (1 % = 100), rounded half up.
     *
     * @return {@code null} when {@code whole} is zero (no share can be given)
     */
    public static Integer shareBps(long part, long whole) {
        if (whole == 0) {
            return null;
        }
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(10_000))
                .divide(BigDecimal.valueOf(whole), 0, RoundingMode.HALF_UP).intValueExact();
    }

    /**
     * Change from {@code previous} to {@code current} in basis points (+12.5 % = 1250).
     *
     * @return {@code null} when {@code previous} is zero (growth from nothing has no percentage)
     */
    public static Long changeBps(long current, long previous) {
        if (previous == 0) {
            return null;
        }
        return BigDecimal.valueOf(current - previous).multiply(BigDecimal.valueOf(10_000))
                .divide(BigDecimal.valueOf(previous), 0, RoundingMode.HALF_UP).longValueExact();
    }

    /** {@code total / count} in cents, rounded half up; zero when there is nothing to divide. */
    public static long averageCents(long totalCents, long count) {
        if (count == 0) {
            return 0;
        }
        return BigDecimal.valueOf(totalCents).divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
