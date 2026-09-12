package com.flowops.analyser.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.analyser.domain.LifecycleRule.Sighting;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ANALYSER-RUN-01")
class ACountMeansNothingWithoutItsDenominatorTest {
    private final LifecycleRule rule = LifecycleRule.reference();

    @Test
    void aFindingWhoseRateFellIsNotWorseningEvenThoughItsCountGrew() {
        Sighting before = new Sighting(100, 500);
        Sighting now = new Sighting(162, 1000);

        assertThat(rule.decide(before, now, false))
                .isNotEqualTo(Lifecycle.WORSENING)
                .isEqualTo(Lifecycle.STILL_TRUE);

        assertThat(rule.decide(Sighting.of(100), Sighting.of(162), false)).isEqualTo(Lifecycle.WORSENING);
    }

    @Test
    void aRateThatFellPastTheDriftIsImproving() {
        assertThat(rule.decide(new Sighting(100, 500), new Sighting(100, 1000), false))
                .isEqualTo(Lifecycle.IMPROVING);
    }

    @Test
    void aShareMustMovePastFivePointsRatherThanReachThem() {
        assertThat(rule.decide(new Sighting(30, 100), new Sighting(35, 100), false))
                .isEqualTo(Lifecycle.STILL_TRUE);
        assertThat(rule.decide(new Sighting(30, 100), new Sighting(36, 100), false))
                .isEqualTo(Lifecycle.WORSENING);
        assertThat(rule.decide(new Sighting(30, 100), new Sighting(25, 100), false))
                .isEqualTo(Lifecycle.STILL_TRUE);
        assertThat(rule.decide(new Sighting(30, 100), new Sighting(24, 100), false))
                .isEqualTo(Lifecycle.IMPROVING);
    }

    @Test
    void aSmallRateNudgingUpwardsIsNoiseEvenWhereTheCountMultiplied() {
        assertThat(rule.decide(new Sighting(2, 100), new Sighting(3, 100), false))
                .isEqualTo(Lifecycle.STILL_TRUE);
        assertThat(rule.decide(Sighting.of(2), Sighting.of(3), false)).isEqualTo(Lifecycle.WORSENING);
    }

    @Test
    void aSightingWithAPopulationIsNotComparedAgainstOneWithout() {
        assertThat(rule.decide(Sighting.of(100), new Sighting(162, 1000), false))
                .isEqualTo(Lifecycle.WORSENING);
        assertThat(rule.decide(new Sighting(100, 500), Sighting.of(162), false)).isEqualTo(Lifecycle.WORSENING);
    }

    @Test
    void aPopulationOfNoneIsNoPopulationAtAll() {
        assertThat(new Sighting(0, 0).hasPopulation()).isFalse();
        assertThat(Sighting.of(41).hasPopulation()).isFalse();
        assertThat(new Sighting(100, 500).hasPopulation()).isTrue();
        assertThat(new Sighting(100, 500).share()).isEqualTo(0.2);

        assertThat(rule.decide(new Sighting(41, 0), new Sighting(62, 0), false)).isEqualTo(Lifecycle.WORSENING);
    }

    @Test
    void aFindingNobodyHasSeenBeforeIsNew() {
        assertThat(rule.decide(null, Sighting.of(41), false)).isEqualTo(Lifecycle.NEW);
        assertThat(rule.decide(null, new Sighting(41, 500), true)).isEqualTo(Lifecycle.NEW);
    }

    @Test
    void aBareCountWorsensAtHalfAgainAndNotBefore() {
        assertThat(rule.decide(Sighting.of(41), Sighting.of(41), false)).isEqualTo(Lifecycle.STILL_TRUE);
        assertThat(rule.decide(Sighting.of(41), Sighting.of(61), false)).isEqualTo(Lifecycle.STILL_TRUE);
        assertThat(rule.decide(Sighting.of(41), Sighting.of(62), false)).isEqualTo(Lifecycle.WORSENING);
    }

    @Test
    void aBareCountThatShrankIsImproving() {
        assertThat(rule.decide(Sighting.of(41), Sighting.of(40), false)).isEqualTo(Lifecycle.IMPROVING);
    }

    @Test
    void growthFromNothingWorsensWithoutMultiplyingByInfinity() {
        assertThat(rule.decide(Sighting.of(0), Sighting.of(1), false)).isEqualTo(Lifecycle.WORSENING);
        assertThat(rule.decide(Sighting.of(0), Sighting.of(0), false)).isEqualTo(Lifecycle.STILL_TRUE);
    }

    @Test
    void aDismissedFindingStaysDismissedWhileItHasNotWorsened() {
        assertThat(rule.decide(Sighting.of(41), Sighting.of(45), true)).isEqualTo(Lifecycle.DISMISSED);
        assertThat(rule.decide(Sighting.of(41), Sighting.of(30), true)).isEqualTo(Lifecycle.DISMISSED);
        assertThat(rule.decide(new Sighting(30, 100), new Sighting(24, 100), true))
                .isEqualTo(Lifecycle.DISMISSED);
    }

    @Test
    void aDismissedFindingReturnsAsWorseningRatherThanAsNew() {
        assertThat(rule.decide(Sighting.of(41), Sighting.of(62), true)).isEqualTo(Lifecycle.WORSENING);
        assertThat(rule.decide(new Sighting(30, 100), new Sighting(36, 100), true))
                .isEqualTo(Lifecycle.WORSENING);
    }

    @Test
    void aFactorThatWouldMakeEveryFindingWorseningIsRefused() {
        assertThatThrownBy(() -> new LifecycleRule(1.0, 0.05)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LifecycleRule(0.5, 0.05)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDriftThatIsNotAFractionOfThePopulationIsRefused() {
        assertThatThrownBy(() -> new LifecycleRule(1.5, 0.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LifecycleRule(1.5, 1.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LifecycleRule(1.5, -0.05)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LifecycleRule(1.5, 1.5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bothThresholdsAreConfigurable() {
        LifecycleRule twiceAsBad = new LifecycleRule(2.0, 0.20);

        assertThat(twiceAsBad.decide(Sighting.of(41), Sighting.of(62), false)).isEqualTo(Lifecycle.STILL_TRUE);
        assertThat(twiceAsBad.decide(Sighting.of(41), Sighting.of(82), false)).isEqualTo(Lifecycle.WORSENING);
        assertThat(twiceAsBad.decide(new Sighting(30, 100), new Sighting(36, 100), false))
                .isEqualTo(Lifecycle.STILL_TRUE);
        assertThat(twiceAsBad.decide(new Sighting(30, 100), new Sighting(55, 100), false))
                .isEqualTo(Lifecycle.WORSENING);
    }
}
