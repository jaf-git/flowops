package com.flowops.shared.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkingCalendarTest {
    private static final ZoneId BUCHAREST = ZoneId.of("Europe/Bucharest");

    private static final WorkingCalendar MONDAY_TO_FRIDAY =
            WorkingCalendar.fromMask("YYYYYNN", LocalTime.of(9, 0), LocalTime.of(17, 0), BUCHAREST);

    private static Instant at(String isoLocal) {
        return java.time.LocalDateTime.parse(isoLocal).atZone(BUCHAREST).toInstant();
    }

    @Test
    @DisplayName("a two-day threshold from Friday afternoon is reached on Tuesday, not on Sunday")
    void theWeekendDoesNotCount() {
        Instant blockedAt = at("2026-08-21T16:00");
        Duration twoWorkingDays = Duration.ofHours(16);

        assertThat(MONDAY_TO_FRIDAY.hasElapsed(blockedAt, at("2026-08-23T16:00"), twoWorkingDays))
                .as("Sunday afternoon: wall-clock says two days have passed and no work happened in them")
                .isFalse();
        assertThat(MONDAY_TO_FRIDAY.hasElapsed(blockedAt, at("2026-08-24T16:00"), twoWorkingDays))
                .as("Monday afternoon: one hour of Friday plus six of Monday is still short of sixteen")
                .isFalse();
        assertThat(MONDAY_TO_FRIDAY.hasElapsed(blockedAt, at("2026-08-25T16:00"), twoWorkingDays))
                .as("Tuesday afternoon: the threshold is reached")
                .isTrue();
    }

    @Test
    @DisplayName("elapsed working time counts only the overlap with each working day")
    void onlyTheOverlapCounts() {
        assertThat(MONDAY_TO_FRIDAY.elapsedWorkingTime(at("2026-08-21T16:00"), at("2026-08-24T10:00")))
                .isEqualTo(Duration.ofHours(2));
    }

    @Test
    @DisplayName("a notice raised at 19:20 on a Friday waits until Monday at nine")
    void theNextWorkingInstantSkipsTheWeekend() {
        assertThat(MONDAY_TO_FRIDAY.nextWorkingInstant(at("2026-08-21T19:20"))).isEqualTo(at("2026-08-24T09:00"));
    }

    @Test
    @DisplayName("an instant already inside working hours waits for nothing")
    void workingTimeIsItsOwnNextInstant() {
        Instant tuesdayMorning = at("2026-08-25T10:00");
        assertThat(MONDAY_TO_FRIDAY.nextWorkingInstant(tuesdayMorning)).isEqualTo(tuesdayMorning);
    }

    @Test
    @DisplayName("before the day opens, the wait is until it opens rather than until tomorrow")
    void earlyMorningWaitsForTodayNotTomorrow() {
        assertThat(MONDAY_TO_FRIDAY.nextWorkingInstant(at("2026-08-25T06:30"))).isEqualTo(at("2026-08-25T09:00"));
    }

    @Test
    @DisplayName("a workspace that never works measures no working time and holds nothing forever")
    void aWorkspaceWithNoWorkingDays() {
        WorkingCalendar never = WorkingCalendar.fromMask("NNNNNNN", LocalTime.of(9, 0), LocalTime.of(17, 0), BUCHAREST);
        Instant someTuesday = at("2026-08-25T10:00");

        assertThat(never.elapsedWorkingTime(at("2026-08-01T00:00"), someTuesday))
                .isEqualTo(Duration.ZERO);
        assertThat(never.nextWorkingInstant(someTuesday)).isEqualTo(someTuesday);
    }
}
