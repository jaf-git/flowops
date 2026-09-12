package com.flowops.aiexport.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class MutableClockTest {
    private static final Instant START = Instant.parse("2026-04-06T08:00:00Z");

    @Test
    void readsTheInstantItWasGiven() {
        MutableClock clock = new MutableClock(START, ZoneId.of("Europe/Bucharest"));

        assertThat(clock.instant()).isEqualTo(START);
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Europe/Bucharest"));
    }

    @Test
    void movesForward() {
        MutableClock clock = new MutableClock(START, ZoneId.of("UTC"));

        clock.advanceTo(START.plus(Duration.ofHours(5)));

        assertThat(clock.instant()).isEqualTo(START.plus(Duration.ofHours(5)));
    }

    @Test
    void refusesToMoveBackwards() {
        MutableClock clock = new MutableClock(START, ZoneId.of("UTC"));
        clock.advanceTo(START.plus(Duration.ofDays(2)));

        assertThatThrownBy(() -> clock.advanceTo(START))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only moves forward");

        assertThat(clock.instant())
                .as("the refused move leaves the clock where it was")
                .isEqualTo(START.plus(Duration.ofDays(2)));
    }

    @Test
    void toleratesAMoveToTheSameInstant() {
        MutableClock clock = new MutableClock(START, ZoneId.of("UTC"));

        clock.advanceTo(START);

        assertThat(clock.instant()).isEqualTo(START);
    }

    @Test
    void withZoneKeepsTheInstantAndChangesOnlyTheZone() {
        MutableClock clock = new MutableClock(START, ZoneId.of("UTC"));

        assertThat(clock.withZone(ZoneId.of("Europe/Bucharest")).instant()).isEqualTo(START);
        assertThat(clock.withZone(ZoneId.of("Europe/Bucharest")).getZone()).isEqualTo(ZoneId.of("Europe/Bucharest"));
    }

    @Test
    void everyReadSeesAnInstantThatWasActuallySet() throws Exception {
        MutableClock clock = new MutableClock(START, ZoneId.of("UTC"));
        List<Instant> legal =
                List.of(START, START.plusSeconds(1), START.plusSeconds(2), START.plusSeconds(3), START.plusSeconds(4));
        AtomicBoolean sawSomethingImpossible = new AtomicBoolean(false);
        CountDownLatch go = new CountDownLatch(1);

        ExecutorService threads = Executors.newFixedThreadPool(4);
        try {
            for (int reader = 0; reader < 3; reader++) {
                threads.submit(() -> {
                    go.await();
                    for (int i = 0; i < 20_000; i++) {
                        if (!legal.contains(clock.instant())) {
                            sawSomethingImpossible.set(true);
                        }
                    }
                    return null;
                });
            }
            threads.submit(() -> {
                go.await();
                for (int i = 1; i < legal.size(); i++) {
                    clock.advanceTo(legal.get(i));
                    Thread.sleep(1);
                }
                return null;
            });
            go.countDown();
            threads.shutdown();
            assertThat(threads.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            threads.shutdownNow();
        }

        assertThat(sawSomethingImpossible).isFalse();
        assertThat(clock.instant()).isEqualTo(legal.get(legal.size() - 1));
    }
}
