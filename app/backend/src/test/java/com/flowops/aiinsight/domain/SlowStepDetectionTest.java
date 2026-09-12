package com.flowops.aiinsight.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.aiinsight.domain.SlowStepDetection.SlowStep;
import com.flowops.aiinsight.domain.SlowStepDetection.StepDuration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("AI-INSIGHT-SLOW-STEP-01")
class SlowStepDetectionTest {
    private static final long HOUR = 3_600_000L;

    private static StepDuration step(String title, long workHours, long waitingHours) {
        return new StepDuration(title, workHours * HOUR, 0, waitingHours * HOUR, 0);
    }

    @Test
    void namesTheStepWithTheLongestMedianElapsed() {
        List<StepDuration> steps = new ArrayList<>();
        for (int run = 0; run < 5; run++) {
            steps.add(step("Discovery", 2, 1));
            steps.add(step("Contract", 3, 40));
            steps.add(step("Kickoff", 2, 2));
        }

        Optional<SlowStep> slowest = SlowStepDetection.over(steps, 5);

        assertThat(slowest).isPresent();
        assertThat(slowest.get().title()).isEqualTo("Contract");
        assertThat(slowest.get().stepsCounted()).isEqualTo(5);
    }

    @Test
    void reportsThePhaseSplitRatherThanOnlyATotal() {
        List<StepDuration> steps = new ArrayList<>();
        for (int run = 0; run < 3; run++) {
            steps.add(step("Contract", 3, 40));
            steps.add(step("Kickoff", 1, 1));
        }

        SlowStep slowest = SlowStepDetection.over(steps, 3).orElseThrow();

        assertThat(slowest.medianWaitingMs()).isEqualTo(40 * HOUR);
        assertThat(slowest.medianWorkMs()).isEqualTo(3 * HOUR);
        assertThat(slowest.medianWaitingMs())
                .as("this step is slow because it waits, not because the work is long — and the two"
                        + " have entirely different remedies")
                .isGreaterThan(slowest.medianWorkMs());
    }

    @Test
    void survivesAnOutlierBecauseItTakesTheMedian() {
        List<StepDuration> steps = new ArrayList<>();
        for (int run = 0; run < 4; run++) {
            steps.add(step("Discovery", 2, 1));
            steps.add(step("Contract", 3, 5));
        }

        steps.add(step("Discovery", 2, 400));
        steps.add(step("Contract", 3, 5));

        assertThat(SlowStepDetection.over(steps, 5).orElseThrow().title()).isEqualTo("Contract");
    }

    @Test
    void saysNothingBelowThreeCompletedRuns() {
        List<StepDuration> steps = List.of(step("Contract", 3, 40), step("Kickoff", 1, 1));

        assertThat(SlowStepDetection.over(steps, 2)).isEmpty();
    }

    @Test
    void saysNothingWhenThereIsOnlyOneKindOfStep() {
        List<StepDuration> steps = List.of(step("Contract", 3, 40), step("Contract", 3, 30), step("Contract", 3, 50));

        assertThat(SlowStepDetection.over(steps, 3)).isEmpty();
    }

    @Test
    void saysNothingWhenNoStepTookMeasurableTime() {
        List<StepDuration> steps = List.of(step("Discovery", 0, 0), step("Contract", 0, 0), step("Kickoff", 0, 0));

        assertThat(SlowStepDetection.over(steps, 4)).isEmpty();
    }

    @Test
    void saysNothingAtAllWithNoHistory() {
        assertThat(SlowStepDetection.over(List.of(), 40)).isEmpty();
    }
}
