package com.flowops.discovery.domain.model;

import java.time.Duration;

public record RhythmWindows(
        Duration idleBeforeNudge, Duration lapseAfterNudge, Duration longExternalWait, Duration standingCadence) {
    public RhythmWindows {
        requirePositive(idleBeforeNudge, "the idle window");
        requirePositive(lapseAfterNudge, "the lapse window");
        requirePositive(longExternalWait, "the long external wait window");
        requirePositive(standingCadence, "the standing cadence");
    }

    public static RhythmWindows defaults() {
        return new RhythmWindows(Duration.ofDays(5), Duration.ofDays(5), Duration.ofDays(7), Duration.ofDays(30));
    }

    private static void requirePositive(Duration value, String what) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(
                    what + " must be a real span; a rhythm with a zero window nudges and closes in one sweep");
        }
    }
}
