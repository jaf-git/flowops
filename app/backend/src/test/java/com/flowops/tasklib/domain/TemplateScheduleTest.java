package com.flowops.tasklib.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TemplateScheduleTest {
    private static final ZoneId ZONE = ZoneId.of("Europe/Bucharest");
    private static final Instant SET_ON = Instant.parse("2026-10-27T04:41:00Z");

    private static TemplateSchedule daily() {
        return new TemplateSchedule(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Recurrence.daily(),
                true,
                SET_ON,
                SET_ON);
    }

    @Test
    @DisplayName("it raises nothing for a date before it existed")
    void refusesAnOccurrenceOlderThanItself() {
        TemplateSchedule schedule = daily();

        assertThat(schedule.fallsDueOn(LocalDate.of(2024, 11, 1), ZONE)).isFalse();

        assertThat(schedule.fallsDueOn(LocalDate.of(2026, 10, 26), ZONE)).isFalse();
    }

    @Test
    @DisplayName("the day it is set is a day it can raise work")
    void raisesOnTheDayItIsSet() {
        assertThat(daily().fallsDueOn(LocalDate.of(2026, 10, 27), ZONE)).isTrue();
        assertThat(daily().fallsDueOn(LocalDate.of(2026, 10, 28), ZONE)).isTrue();
    }

    @Test
    @DisplayName("a paused schedule falls due on nothing")
    void pausedRaisesNothing() {
        TemplateSchedule paused = daily().paused(SET_ON);

        assertThat(paused.fallsDueOn(LocalDate.of(2026, 10, 28), ZONE)).isFalse();
        assertThat(paused.nextOccurrenceOnOrAfter(LocalDate.of(2026, 10, 28)))
                .as("a paused schedule must not answer with a date that will not happen")
                .isNull();
    }

    @Test
    @DisplayName("resuming does not catch up on what it missed")
    void resumingStartsFromNow() {
        TemplateSchedule resumed = daily().paused(SET_ON).resumed(Instant.parse("2026-12-01T09:00:00Z"));

        assertThat(resumed.fallsDueOn(LocalDate.of(2026, 12, 1), ZONE)).isTrue();
        assertThat(resumed.fallsDueOn(LocalDate.of(2026, 10, 26), ZONE)).isFalse();
    }
}
