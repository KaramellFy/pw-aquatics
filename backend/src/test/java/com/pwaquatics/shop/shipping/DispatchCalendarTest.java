package com.pwaquatics.shop.shipping;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pwaquatics.shop.shipping.domain.DispatchCalendar;
import com.pwaquatics.shop.shipping.domain.ShippingPause;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DispatchCalendarTest {

    private static final Set<DayOfWeek> MON_TO_WED = Set.of(MONDAY, TUESDAY, WEDNESDAY);
    // 9 November 2026 is a Monday.
    private static final LocalDate MONDAY_9TH = LocalDate.of(2026, 11, 9);

    @Test
    void parcelsLeaveOnTheNextDispatchWeekday() {
        assertThat(MONDAY_9TH.getDayOfWeek()).isEqualTo(MONDAY);
        // Ordered on Monday with one handling day: Tuesday.
        assertThat(DispatchCalendar.plannedDispatchDate(MONDAY_9TH, 1, MON_TO_WED, null))
                .isEqualTo(LocalDate.of(2026, 11, 10));
        // Ordered on Tuesday: Wednesday is still a dispatch day.
        assertThat(DispatchCalendar.plannedDispatchDate(MONDAY_9TH.plusDays(1), 1, MON_TO_WED, null))
                .isEqualTo(LocalDate.of(2026, 11, 11));
        // Ordered on Wednesday or later: nothing leaves before the weekend, so next Monday.
        for (int offset = 2; offset <= 6; offset++) {
            assertThat(DispatchCalendar.plannedDispatchDate(MONDAY_9TH.plusDays(offset), 1, MON_TO_WED, null))
                    .isEqualTo(LocalDate.of(2026, 11, 16));
        }
        // Without handling time an order can leave the same day.
        assertThat(DispatchCalendar.plannedDispatchDate(MONDAY_9TH, 0, MON_TO_WED, null)).isEqualTo(MONDAY_9TH);
    }

    @Test
    void aShippingPauseMovesTheDispatchBehindItsLastDay() {
        // Paused until Tuesday 17th: first dispatch on Wednesday 18th.
        assertThat(DispatchCalendar.plannedDispatchDate(MONDAY_9TH, 1, MON_TO_WED, LocalDate.of(2026, 11, 17)))
                .isEqualTo(LocalDate.of(2026, 11, 18));
        // Paused until Wednesday 18th: the week is lost, next Monday.
        assertThat(DispatchCalendar.plannedDispatchDate(MONDAY_9TH, 1, MON_TO_WED, LocalDate.of(2026, 11, 18)))
                .isEqualTo(LocalDate.of(2026, 11, 23));
        // A pause that ended before the parcel could leave anyway changes nothing.
        assertThat(DispatchCalendar.plannedDispatchDate(MONDAY_9TH, 1, MON_TO_WED, MONDAY_9TH))
                .isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    void aPauseCountsUntilItsLastDayAndCanBeOpenEnded() {
        ShippingPause untilTuesday = new ShippingPause(true, "Frost", LocalDate.of(2026, 11, 10));
        assertThat(untilTuesday.isInForce(LocalDate.of(2026, 11, 10))).isTrue();
        assertThat(untilTuesday.isInForce(LocalDate.of(2026, 11, 11))).isFalse();
        assertThat(untilTuesday.isOpenEnded()).isFalse();

        ShippingPause openEnded = new ShippingPause(true, null, null);
        assertThat(openEnded.isInForce(LocalDate.of(2030, 1, 1))).isTrue();
        assertThat(openEnded.isOpenEnded()).isTrue();

        assertThat(new ShippingPause(false, null, LocalDate.of(2030, 1, 1)).isInForce(MONDAY_9TH)).isFalse();
        assertThat(ShippingPause.NONE.isInForce(MONDAY_9TH)).isFalse();
    }

    @Test
    void aMethodWithoutDispatchDaysIsRejected() {
        assertThatThrownBy(() -> DispatchCalendar.nextDispatchDate(MONDAY_9TH, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
