package com.flowops.tasklib.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TemplatePerformanceTest {
    @Test
    void twoMeasurementsAreTooFewToAverage() {
        assertThat(measured(2).tooFewToAverage()).isTrue();
    }

    @Test
    void threeIsEnough() {
        assertThat(measured(3).tooFewToAverage()).isFalse();
        assertThat(TemplatePerformance.ENOUGH_TO_AVERAGE).isEqualTo(3);
    }

    @Test
    void aTemplateNeverUsedSaysSoRatherThanReportingZeroHours() {
        TemplatePerformance never = new TemplatePerformance(0, 0, null, null, null, 0, 0);

        assertThat(never.neverUsed()).isTrue();
        assertThat(never.medianActiveSeconds()).isNull();
    }

    @Test
    void aWideSpreadIsFlaggedSoOneFigureIsNotPresentedAsTheAnswer() {
        TemplatePerformance wide = new TemplatePerformance(9, 9, 14_400L, 7_200L, 43_200L, 5, 9);

        assertThat(wide.varyWidely()).isTrue();
    }

    @Test
    void aTightSpreadIsNotFlagged() {
        TemplatePerformance tight = new TemplatePerformance(9, 9, 7_500L, 7_200L, 8_100L, 8, 9);

        assertThat(tight.varyWidely()).isFalse();
    }

    @Test
    void aThinSampleIsNeverCalledWideBecauseTwoNumbersHaveNoSpread() {
        TemplatePerformance thin = new TemplatePerformance(2, 2, 14_400L, 3_600L, 43_200L, 1, 2);

        assertThat(thin.varyWidely()).isFalse();
        assertThat(thin.tooFewToAverage()).isTrue();
    }

    private TemplatePerformance measured(int tasks) {
        return new TemplatePerformance(tasks, tasks, 7_200L, 5_400L, 9_000L, tasks, tasks);
    }
}
