package com.flowops.aiexport.seed;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;

final class SeedSchedule {
    private static final int OVERNIGHT_HOURS = 16;

    private final MutableClock clock;
    private final Random spread;

    SeedSchedule(MutableClock clock, long randomSeed) {
        this.clock = clock;
        this.spread = new Random(randomSeed);
    }

    Instant now() {
        return clock.instant();
    }

    Instant deadlineIn(int days) {
        return clock.instant().plus(Duration.ofDays(days));
    }

    void advance(Duration by) {
        clock.advanceTo(clock.instant().plus(by));
    }

    void advanceHours(long hours) {
        advance(Duration.ofHours(hours));
    }

    void advanceMinutes(long minutes) {
        advance(Duration.ofMinutes(minutes));
    }

    void nextWorkingMorning() {
        Instant next = clock.instant().plus(Duration.ofHours(OVERNIGHT_HOURS));
        while (isWeekend(next)) {
            next = next.plus(Duration.ofDays(1));
        }
        clock.advanceTo(next);
    }

    private boolean isWeekend(Instant instant) {
        DayOfWeek day = instant.atZone(clock.getZone()).getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    boolean jumpTo(Instant anchor) {
        if (anchor.isAfter(clock.instant())) {
            clock.advanceTo(anchor);
            return true;
        }
        return false;
    }

    int hoursAround(int typical) {
        int lowest = Math.max(1, typical - typical / 2);
        return lowest + spread.nextInt(Math.max(1, typical));
    }

    int occasionallyMuchLonger(int typical) {
        return spread.nextInt(10) == 0 ? typical * 4 : hoursAround(typical);
    }

    int upTo(int bound) {
        return spread.nextInt(Math.max(1, bound));
    }
}
