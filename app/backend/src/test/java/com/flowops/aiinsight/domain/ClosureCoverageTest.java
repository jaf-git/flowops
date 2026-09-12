package com.flowops.aiinsight.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClosureCoverageTest {
    private static final int DEFAULT_THRESHOLD = 90;

    @Test
    @DisplayName("forty closed of sixty-six states both figures")
    void aThinlyClosedPopulationDiscloses() {
        ClosureCoverage coverage = ClosureCoverage.materialIn(40, 66, DEFAULT_THRESHOLD);

        assertThat(coverage).isNotNull();
        assertThat(coverage.excluded())
                .as("the twenty-six are the whole point: they are where the difficult work went")
                .isEqualTo(26);
        assertThat(coverage.qualifying())
                .as("two figures rather than a percentage, because 26 of 66 is checkable and 61% is not")
                .isEqualTo(66);
    }

    @Test
    @DisplayName("a population that closed entirely discloses nothing, and answers null rather than zero")
    void nothingExcludedIsAbsentRatherThanZero() {
        assertThat(ClosureCoverage.materialIn(12, 12, DEFAULT_THRESHOLD))
                .as("AI_INSIGHT_04 section 5 refuses \"0 further tasks never closed\" by name; null cannot be"
                        + " rendered by accident where a zero can")
                .isNull();
    }

    @Test
    @DisplayName("an exclusion inside the configured share is not worth a clause")
    void anImmaterialExclusionStaysSilent() {
        assertThat(ClosureCoverage.materialIn(95, 100, DEFAULT_THRESHOLD))
                .as("a clause on every card is the noise the evidence rule already refused once")
                .isNull();
    }

    @Test
    @DisplayName("the same population discloses or stays silent according to the workspace's own threshold")
    void thresholdRatherThanFixtureDecides() {
        assertThat(ClosureCoverage.materialIn(80, 100, 90))
                .as("eighty per cent closed is thin for a workspace that expects ninety")
                .isNotNull();
        assertThat(ClosureCoverage.materialIn(80, 100, 70))
                .as("the identical population is comfortable for a workspace that expects seventy — so the"
                        + " threshold is doing the work, not the numbers")
                .isNull();
    }

    @Test
    @DisplayName("a small sample is not rounded under the threshold and lost")
    void integerDivisionDoesNotSilenceTheSmallestSamples() {
        assertThat(ClosureCoverage.materialIn(2, 3, DEFAULT_THRESHOLD))
                .as("two of three is sixty-seven per cent; dividing rather than multiplying out would have"
                        + " rounded exactly the samples most in need of a warning into silence")
                .isNotNull();
    }

    @Test
    @DisplayName("an empty population says nothing rather than dividing by zero")
    void anEmptyPopulationIsSilent() {
        assertThat(ClosureCoverage.materialIn(0, 0, DEFAULT_THRESHOLD)).isNull();
    }
}
