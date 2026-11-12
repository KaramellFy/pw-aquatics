package com.pwaquatics.shop.admin.domain;

/** Size of one bucket in a statistics time series. Weeks start on Monday (ISO 8601). */
public enum Granularity {
    DAY,
    WEEK,
    MONTH;

    /** The unit name PostgreSQL's {@code date_trunc} expects. */
    public String sqlUnit() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
