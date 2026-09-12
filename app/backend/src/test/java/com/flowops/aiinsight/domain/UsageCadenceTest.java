package com.flowops.aiinsight.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.aiinsight.domain.UnusedTemplateDetection.TemplateUsage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UsageCadenceTest {
    private static final Instant TODAY = Instant.parse("2026-09-29T09:00:00Z");
    private static final int FIXED_WINDOW = 90;

    @Test
    @DisplayName("a quarterly template last used eighty days ago is on schedule, not idle")
    void aTemplateOnItsOwnRhythmIsNotReported() {
        TemplateUsage quarterly = usedEvery(89, 3, daysAgo(80));

        assertThat(UnusedTemplateDetection.over(List.of(quarterly), TODAY, FIXED_WINDOW))
                .as("the fixed window would have proposed retiring exactly the seasonal work most worth keeping")
                .isEmpty();
    }

    @Test
    @DisplayName("the same template two hundred days on has missed its occurrence and is reported")
    void aTemplatePastItsRhythmIsReportedAndNamesIt() {
        TemplateUsage quarterly = usedEvery(89, 3, daysAgo(200));

        assertThat(UnusedTemplateDetection.over(List.of(quarterly), TODAY, FIXED_WINDOW))
                .singleElement()
                .satisfies(stale -> {
                    assertThat(stale.judgedAgainstARhythm())
                            .as("the insight states which rule produced it, so the reader can see a rhythm was found")
                            .isTrue();
                    assertThat(stale.expectedIntervalDays()).isEqualTo(89);
                    assertThat(stale.windowDays())
                            .as("the allowance is the rhythm's own, not the fixed window")
                            .isGreaterThan(FIXED_WINDOW);
                });
    }

    @Test
    @DisplayName("fewer than three uses shows no rhythm, and the fixed window applies unchanged")
    void tooFewUsesFallsBackToTheFixedWindow() {
        TemplateUsage twice = new TemplateUsage(
                UUID.randomUUID(),
                "Fire drill notice",
                daysAgo(400),
                daysAgo(120),
                2,
                List.of(daysAgo(300), daysAgo(120)));

        assertThat(UnusedTemplateDetection.over(List.of(twice), TODAY, FIXED_WINDOW))
                .singleElement()
                .satisfies(stale -> {
                    assertThat(stale.judgedAgainstARhythm())
                            .as("one interval is a single observation, and calling it a rhythm is the confident"
                                    + " wrong answer the type exists to avoid")
                            .isFalse();
                    assertThat(stale.windowDays()).isEqualTo(FIXED_WINDOW);
                });
    }

    @Test
    @DisplayName("intervals that do not cluster show no rhythm")
    void scatteredUseIsNotARhythm() {
        List<Instant> scattered = List.of(daysAgo(500), daysAgo(400), daysAgo(370), daysAgo(120));

        assertThat(UsageCadence.in(scattered).isDetected()).isFalse();
        assertThat(UsageCadence.in(scattered).idleAfterDays(FIXED_WINDOW))
                .as("no rhythm means the configured window, by the same code path, byte for byte")
                .isEqualTo(FIXED_WINDOW);
    }

    @Test
    @DisplayName("a template nobody ever used has no rhythm to be on schedule against")
    void neverUsedHasNoCadence() {
        assertThat(UsageCadence.in(List.of()).isDetected()).isFalse();
        assertThat(UsageCadence.in(List.of()).idleAfterDays(FIXED_WINDOW)).isEqualTo(FIXED_WINDOW);
    }

    @Test
    @DisplayName("one irregular use does not defeat a rhythm the rest of the history agrees on")
    void oneOddUseDoesNotDestroyARhythm() {
        List<Instant> monthlyWithOneSlip =
                List.of(daysAgo(180), daysAgo(150), daysAgo(120), daysAgo(112), daysAgo(90), daysAgo(60));

        assertThat(UsageCadence.in(monthlyWithOneSlip).isDetected())
                .as("a rule demanding every interval agree is defeated by one irregular use in two regular years")
                .isTrue();
    }

    private static TemplateUsage usedEvery(int days, int uses, Instant lastUsed) {
        List<Instant> history = new ArrayList<>();
        for (int use = uses - 1; use >= 0; use--) {
            history.add(lastUsed.minus(Duration.ofDays((long) days * use)));
        }
        return new TemplateUsage(
                UUID.randomUUID(), "Quarterly VAT return", daysAgo(900), lastUsed, uses, List.copyOf(history));
    }

    private static Instant daysAgo(int days) {
        return TODAY.minus(Duration.ofDays(days));
    }
}
