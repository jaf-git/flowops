package com.flowops.analyser.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ANALYSER-VIEW-FINDINGS-01")
class WhereAFindingSitsInTheQueueTest {
    private final PriorityRule rule = PriorityRule.reference();

    private double score(Severity severity, Confidence confidence, int reach, Integer reachOf, Lifecycle lifecycle) {
        return rule.rank(severity, confidence, reach, reachOf, lifecycle, 1).score();
    }

    @Test
    void aCertainEmergencyOutranksACertainTriviality() {
        double emergency = score(Severity.CRITICAL, Confidence.HIGH, 10, 100, Lifecycle.NEW);
        double triviality = score(Severity.LOW, Confidence.HIGH, 10, 100, Lifecycle.NEW);

        assertThat(emergency).isGreaterThan(triviality);
    }

    @Test
    void anUncertainFindingIsRankedLowRatherThanDeleted() {
        double uncertain = score(Severity.CRITICAL, Confidence.LOW, 10, 100, Lifecycle.NEW);
        double certain = score(Severity.CRITICAL, Confidence.HIGH, 10, 100, Lifecycle.NEW);

        assertThat(uncertain).isGreaterThan(0.0).isLessThan(certain);
    }

    @Test
    void theTrendOrdersWorseningAboveNewAboveUnchangedAboveImproving() {
        double worsening = score(Severity.HIGH, Confidence.HIGH, 10, 100, Lifecycle.WORSENING);
        double fresh = score(Severity.HIGH, Confidence.HIGH, 10, 100, Lifecycle.NEW);
        double unchanged = score(Severity.HIGH, Confidence.HIGH, 10, 100, Lifecycle.STILL_TRUE);
        double improving = score(Severity.HIGH, Confidence.HIGH, 10, 100, Lifecycle.IMPROVING);

        assertThat(worsening).isGreaterThan(fresh);
        assertThat(fresh).isGreaterThan(unchanged);
        assertThat(unchanged).isGreaterThan(improving);
    }

    @Test
    void aFindingThatTouchesMostOfThePopulationOutranksOneThatTouchesAFraction() {
        double most = score(Severity.HIGH, Confidence.HIGH, 90, 100, Lifecycle.NEW);
        double little = score(Severity.HIGH, Confidence.HIGH, 3, 100, Lifecycle.NEW);

        assertThat(most).isGreaterThan(little);
    }

    @Test
    void aCountWithNoDenominatorIsNotTreatedAsAShare() {
        double seven = score(Severity.HIGH, Confidence.HIGH, 7, null, Lifecycle.NEW);
        double sevenHundred = score(Severity.HIGH, Confidence.HIGH, 700, null, Lifecycle.NEW);

        assertThat(seven).isEqualTo(sevenHundred);

        assertThat(seven)
                .isGreaterThan(score(Severity.HIGH, Confidence.HIGH, 0, 100, Lifecycle.NEW))
                .isLessThan(score(Severity.HIGH, Confidence.HIGH, 100, 100, Lifecycle.NEW));
    }

    @Test
    void nothingFadesWithinTheGrace() {
        double first = rule.rank(Severity.HIGH, Confidence.HIGH, 40, 60, Lifecycle.STILL_TRUE, 1)
                .score();
        double second = rule.rank(Severity.HIGH, Confidence.HIGH, 40, 60, Lifecycle.STILL_TRUE, 2)
                .score();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void aFindingNobodyActedOnSinksFurtherEachRun() {
        double third = rule.rank(Severity.HIGH, Confidence.HIGH, 40, 60, Lifecycle.STILL_TRUE, 3)
                .score();
        double sixth = rule.rank(Severity.HIGH, Confidence.HIGH, 40, 60, Lifecycle.STILL_TRUE, 6)
                .score();
        double second = rule.rank(Severity.HIGH, Confidence.HIGH, 40, 60, Lifecycle.STILL_TRUE, 2)
                .score();

        assertThat(third).isLessThan(second);
        assertThat(sixth).isLessThan(third);
    }

    @Test
    void aFindingIgnoredForeverIsQuietRatherThanGone() {
        double afterFifty = rule.rank(Severity.HIGH, Confidence.HIGH, 40, 60, Lifecycle.STILL_TRUE, 50)
                .score();
        double afterAHundred = rule.rank(Severity.HIGH, Confidence.HIGH, 40, 60, Lifecycle.STILL_TRUE, 100)
                .score();

        assertThat(afterFifty).isGreaterThan(0.0);

        assertThat(afterAHundred).isEqualTo(afterFifty);
    }

    @Test
    void worseningNeverFadesHoweverManyTimesItHasBeenShown() {
        double onceWorse = rule.rank(Severity.HIGH, Confidence.HIGH, 40, 60, Lifecycle.WORSENING, 1)
                .score();
        double longIgnoredAndNowWorse = rule.rank(Severity.HIGH, Confidence.HIGH, 40, 60, Lifecycle.WORSENING, 13)
                .score();

        assertThat(longIgnoredAndNowWorse).isEqualTo(onceWorse);
    }

    @Test
    void theRankArrivesWithTheOneLineThatExplainsIt() {
        String because = rule.rank(Severity.HIGH, Confidence.HIGH, 41, 63, Lifecycle.WORSENING, 1)
                .because();

        assertThat(because).isEqualTo("High because it touches 41 of 63 and it is worse than last run.");
    }

    @Test
    void aFindingWithNoPopulationDoesNotInventOneToBeOutOf() {
        String because = rule.rank(Severity.MEDIUM, Confidence.HIGH, 7, null, Lifecycle.NEW, 1)
                .because();

        assertThat(because).isEqualTo("Medium because it touches 7 and it is new since the last run.");
    }

    @Test
    void aFadedFindingSaysHowManyTimesItHasBeenShown() {
        String because = rule.rank(Severity.HIGH, Confidence.HIGH, 41, 63, Lifecycle.STILL_TRUE, 6)
                .because();

        assertThat(because).contains("Shown 6 times, unchanged.");
    }

    @Test
    void aFindingThatHasNotFadedDoesNotCountItsAppearances() {
        String because = rule.rank(Severity.HIGH, Confidence.HIGH, 41, 63, Lifecycle.STILL_TRUE, 2)
                .because();

        assertThat(because).doesNotContain("Shown");
    }

    @Test
    void anUncertainFindingSaysSoInWords() {
        String because = rule.rank(Severity.CRITICAL, Confidence.LOW, 4, 9, Lifecycle.NEW, 1)
                .because();

        assertThat(because).contains("The analyser is not certain of this one.");
    }

    @Test
    void aFindingWithNoSeverityWeighsLeastRatherThanLosingTheQueue() {
        PriorityRule.Ranked unranked = rule.rank(null, null, 40, 60, Lifecycle.NEW, 1);

        assertThat(unranked.score()).isGreaterThan(0.0);
        assertThat(unranked.score())
                .isLessThanOrEqualTo(score(Severity.LOW, Confidence.LOW, 40, 60, Lifecycle.NEW))
                .isLessThan(score(Severity.MEDIUM, Confidence.LOW, 40, 60, Lifecycle.NEW));
        assertThat(unranked.because()).startsWith("Unranked because");
    }

    @Test
    void aConfigurationThatWouldMakeTheFadeMeaninglessIsRefused() {
        assertThatThrownBy(() -> new PriorityRule(0, 0.8, 0.25))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("grace");
        assertThatThrownBy(() -> new PriorityRule(2, 1.0, 0.25))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("never fades");
        assertThatThrownBy(() -> new PriorityRule(2, 0.8, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disappear");
    }
}
