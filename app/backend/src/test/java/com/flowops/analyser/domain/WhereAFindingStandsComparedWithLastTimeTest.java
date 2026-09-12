package com.flowops.analyser.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.analyser.domain.LifecycleRule.Sighting;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ANALYSER-RUN-01")
class WhereAFindingStandsComparedWithLastTimeTest {
    private final LifecycleRule rule = LifecycleRule.reference();

    @Test
    void aFindingNobodyHasSeenBeforeIsNew() {
        assertThat(rule.decide(null, Sighting.of(41), false)).isEqualTo(Lifecycle.NEW);
    }

    @Test
    void aFindingAtTheSameReachIsStillTrue() {
        assertThat(rule.decide(Sighting.of(41), Sighting.of(41), false)).isEqualTo(Lifecycle.STILL_TRUE);
    }

    @Test
    void growthShortOfHalfAgainIsStillTrue() {
        assertThat(rule.decide(Sighting.of(41), Sighting.of(61), false)).isEqualTo(Lifecycle.STILL_TRUE);
    }

    @Test
    void growthOfHalfAgainIsWorsening() {
        assertThat(rule.decide(Sighting.of(41), Sighting.of(62), false)).isEqualTo(Lifecycle.WORSENING);
    }

    @Test
    void shrinkingIsImproving() {
        assertThat(rule.decide(Sighting.of(41), Sighting.of(30), false)).isEqualTo(Lifecycle.IMPROVING);
    }

    @Test
    void aDismissedFindingStaysDismissedWhileItHasNotGrown() {
        assertThat(rule.decide(Sighting.of(41), Sighting.of(45), true)).isEqualTo(Lifecycle.DISMISSED);
    }

    @Test
    void aDismissedFindingReturnsAsWorseningRatherThanAsNew() {
        assertThat(rule.decide(Sighting.of(41), Sighting.of(62), true)).isEqualTo(Lifecycle.WORSENING);
    }

    @Test
    void growthFromNothingWorsensWithoutMultiplyingByInfinity() {
        assertThat(rule.decide(Sighting.of(0), Sighting.of(1), false)).isEqualTo(Lifecycle.WORSENING);
        assertThat(rule.decide(Sighting.of(0), Sighting.of(0), false)).isEqualTo(Lifecycle.STILL_TRUE);
    }

    @Test
    void aFactorThatWouldMakeEveryFindingWorseningIsRefused() {
        assertThatThrownBy(() -> new LifecycleRule(1.0, LifecycleRule.DEFAULT_SHARE_DRIFT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LifecycleRule(0.5, LifecycleRule.DEFAULT_SHARE_DRIFT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theFactorIsConfigurable() {
        LifecycleRule twiceAsBad = new LifecycleRule(2.0, LifecycleRule.DEFAULT_SHARE_DRIFT);

        assertThat(twiceAsBad.decide(Sighting.of(41), Sighting.of(62), false)).isEqualTo(Lifecycle.STILL_TRUE);
        assertThat(twiceAsBad.decide(Sighting.of(41), Sighting.of(82), false)).isEqualTo(Lifecycle.WORSENING);
    }
}
