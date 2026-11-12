package com.pwaquatics.shop.admin.domain;

import com.pwaquatics.shop.common.error.BusinessRuleException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A reporting period of whole calendar days in the shop's time zone, both ends inclusive.
 *
 * <p>The bucket size follows the length, so a chart never has more than about 31 columns:
 * up to 31 days by day, up to 26 weeks by week, longer periods by month. The comparison
 * period is the same number of days directly before.
 */
public record StatsPeriod(LocalDate from, LocalDate to, Granularity granularity) {

    /** Longest period that can be requested (two years plus a leap day). */
    public static final int MAX_DAYS = 731;

    public StatsPeriod {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(granularity, "granularity");
    }

    /**
     * @throws BusinessRuleException {@code STATS_RANGE_INVALID} when {@code to} is before
     *     {@code from} or the period is longer than {@link #MAX_DAYS}
     */
    public static StatsPeriod of(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessRuleException("STATS_RANGE_INVALID", "The end date is before the start date.");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_DAYS) {
            throw new BusinessRuleException("STATS_RANGE_INVALID", "A period can be at most two years long.");
        }
        Granularity granularity = days <= 31 ? Granularity.DAY : days <= 182 ? Granularity.WEEK : Granularity.MONTH;
        return new StatsPeriod(from, to, granularity);
    }

    /** Number of days in the period. */
    public long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    /** The period of equal length that ends the day before this one starts. */
    public StatsPeriod previous() {
        LocalDate previousTo = from.minusDays(1);
        return new StatsPeriod(previousTo.minusDays(days() - 1), previousTo, granularity);
    }

    /** First instant of the period in the given zone. */
    public Instant start(ZoneId zone) {
        return from.atStartOfDay(zone).toInstant();
    }

    /** First instant after the period (exclusive end) in the given zone. */
    public Instant end(ZoneId zone) {
        return to.plusDays(1).atStartOfDay(zone).toInstant();
    }

    /** The bucket a day belongs to: the day itself, its week's Monday or its month's first day. */
    public LocalDate bucketOf(LocalDate day) {
        return switch (granularity) {
            case DAY -> day;
            case WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> day.withDayOfMonth(1);
        };
    }

    /** Start dates of every bucket that overlaps the period, oldest first, without gaps. */
    public List<LocalDate> buckets() {
        List<LocalDate> buckets = new ArrayList<>();
        for (LocalDate bucket = bucketOf(from); !bucket.isAfter(to); bucket = next(bucket)) {
            buckets.add(bucket);
        }
        return buckets;
    }

    private LocalDate next(LocalDate bucket) {
        return switch (granularity) {
            case DAY -> bucket.plusDays(1);
            case WEEK -> bucket.plusWeeks(1);
            case MONTH -> bucket.plusMonths(1);
        };
    }
}
