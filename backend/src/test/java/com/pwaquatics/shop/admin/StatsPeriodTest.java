package com.pwaquatics.shop.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pwaquatics.shop.admin.domain.Granularity;
import com.pwaquatics.shop.admin.domain.Ratios;
import com.pwaquatics.shop.admin.domain.StatsPeriod;
import com.pwaquatics.shop.common.error.BusinessRuleException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class StatsPeriodTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void theBucketSizeFollowsTheLength() {
        assertThat(period("2026-11-01", "2026-12-01").granularity()).isEqualTo(Granularity.DAY); // 31 days
        assertThat(period("2026-11-01", "2026-12-02").granularity()).isEqualTo(Granularity.WEEK);
        assertThat(period("2026-01-01", "2026-07-01").granularity()).isEqualTo(Granularity.WEEK); // 182 days
        assertThat(period("2026-01-01", "2026-07-02").granularity()).isEqualTo(Granularity.MONTH);
    }

    @Test
    void bucketsCoverThePeriodWithoutGaps() {
        // Wednesday 4th to Monday 16th: the first week starts on Monday 2nd
        StatsPeriod weeks = new StatsPeriod(LocalDate.parse("2026-11-04"), LocalDate.parse("2026-11-16"),
                Granularity.WEEK);
        assertThat(weeks.buckets()).extracting(LocalDate::toString)
                .containsExactly("2026-11-02", "2026-11-09", "2026-11-16");
        StatsPeriod months = new StatsPeriod(LocalDate.parse("2026-01-31"), LocalDate.parse("2026-03-01"),
                Granularity.MONTH);
        assertThat(months.buckets()).extracting(LocalDate::toString)
                .containsExactly("2026-01-01", "2026-02-01", "2026-03-01");
        assertThat(period("2026-11-01", "2026-11-03").buckets()).hasSize(3);
    }

    @Test
    void thePreviousPeriodHasTheSameLengthAndEndsTheDayBefore() {
        StatsPeriod previous = period("2026-03-01", "2026-03-31").previous();
        assertThat(previous.from()).isEqualTo("2026-01-29");
        assertThat(previous.to()).isEqualTo("2026-02-28");
    }

    @Test
    void daysStartAndEndAtMidnightInShopTime() {
        // the clocks go forward on 29 March 2026: that day has 23 hours
        StatsPeriod day = period("2026-03-29", "2026-03-29");
        assertThat(day.start(BERLIN)).isEqualTo(Instant.parse("2026-03-28T23:00:00Z"));
        assertThat(day.end(BERLIN)).isEqualTo(Instant.parse("2026-03-29T22:00:00Z"));
    }

    @Test
    void invalidPeriodsAreRejected() {
        assertThatThrownBy(() -> period("2026-11-02", "2026-11-01"))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "STATS_RANGE_INVALID");
        assertThat(period("2025-01-01", "2026-12-31").days()).isEqualTo(730);
        assertThatThrownBy(() -> period("2024-01-01", "2026-01-01"))
                .hasFieldOrPropertyWithValue("code", "STATS_RANGE_INVALID");
    }

    @Test
    void sharesChangesAndAveragesAreRoundedHalfUp() {
        assertThat(Ratios.shareBps(1, 3)).isEqualTo(3333);
        assertThat(Ratios.shareBps(2, 3)).isEqualTo(6667);
        assertThat(Ratios.shareBps(1, 0)).isNull();
        assertThat(Ratios.changeBps(150, 100)).isEqualTo(5000);
        assertThat(Ratios.changeBps(50, 100)).isEqualTo(-5000);
        assertThat(Ratios.changeBps(5, 0)).isNull();
        assertThat(Ratios.averageCents(14480, 3)).isEqualTo(4827);
        assertThat(Ratios.averageCents(100, 0)).isZero();
    }

    private static StatsPeriod period(String from, String to) {
        return StatsPeriod.of(LocalDate.parse(from), LocalDate.parse(to));
    }
}
