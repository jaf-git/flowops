package com.flowops.analyser.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ANALYSER-DISMISS-FINDING-01")
class ADismissalIsNotATimerTest {
    private final LifecycleRule lifecycle = LifecycleRule.reference();

    private static FindingFingerprint at(int reach, Integer reachOf) {
        return FindingFingerprint.of(Severity.HIGH, Confidence.HIGH, "Review the library", reach, reachOf);
    }

    @Test
    void aFindingAtTheSizeItWasDismissedAtStaysDismissed() {
        FindingFingerprint dismissedAt = at(41, 63);

        Lifecycle now = lifecycle.decide(dismissedAt.sighting(), at(41, 63).sighting(), true);

        assertThat(now).isEqualTo(Lifecycle.DISMISSED);
    }

    @Test
    void aFindingThatCreepsUpwardsStillComesBackWhenItHasGrownByHalf() {
        FindingFingerprint dismissedAt = at(40, null);

        assertThat(lifecycle.decide(dismissedAt.sighting(), at(48, null).sighting(), true))
                .isEqualTo(Lifecycle.DISMISSED);
        assertThat(lifecycle.decide(dismissedAt.sighting(), at(58, null).sighting(), true))
                .isEqualTo(Lifecycle.DISMISSED);

        assertThat(lifecycle.decide(dismissedAt.sighting(), at(70, null).sighting(), true))
                .isEqualTo(Lifecycle.WORSENING);

        assertThat(lifecycle.decide(at(58, null).sighting(), at(70, null).sighting(), true))
                .isEqualTo(Lifecycle.DISMISSED);
    }

    @Test
    void aShareThatDriftsPastTheBandBringsItBack() {
        FindingFingerprint dismissedAt = at(41, 63);

        assertThat(lifecycle.decide(dismissedAt.sighting(), at(62, 64).sighting(), true))
                .isEqualTo(Lifecycle.WORSENING);
    }

    @Test
    void acountThatDoubledBecauseTheLibraryDoubledStaysDismissed() {
        FindingFingerprint dismissedAt = at(41, 63);

        assertThat(lifecycle.decide(dismissedAt.sighting(), at(82, 126).sighting(), true))
                .isEqualTo(Lifecycle.DISMISSED);
    }

    @Test
    void thesameProblemAtTheSameSeverityHasTheSameCharacter() {
        assertThat(at(41, 63).sameCharacterAs(at(62, 64))).isTrue();
    }

    @Test
    void aFindingThatChangedSeverityIsNoLongerTheQuestionThatWasAnswered() {
        FindingFingerprint dismissed =
                FindingFingerprint.of(Severity.MEDIUM, Confidence.HIGH, "Review the library", 41, 63);
        FindingFingerprint now =
                FindingFingerprint.of(Severity.CRITICAL, Confidence.HIGH, "Review the library", 41, 63);

        assertThat(dismissed.sameCharacterAs(now)).isFalse();
    }

    @Test
    void aFindingAskingForADifferentActionIsADifferentQuestion() {
        FindingFingerprint dismissed =
                FindingFingerprint.of(Severity.HIGH, Confidence.HIGH, "Review the library", 41, 63);
        FindingFingerprint now = FindingFingerprint.of(Severity.HIGH, Confidence.HIGH, "Write a template", 41, 63);

        assertThat(dismissed.sameCharacterAs(now)).isFalse();
    }

    @Test
    void aFingerprintSurvivesBeingWrittenDownAndReadBack() {
        FindingFingerprint withPopulation = at(41, 63);
        FindingFingerprint without = at(7, null);

        assertThat(FindingFingerprint.parse(withPopulation.format())).isEqualTo(withPopulation);
        assertThat(FindingFingerprint.parse(without.format())).isEqualTo(without);
        assertThat(FindingFingerprint.parse(without.format()).reachOf()).isNull();
    }

    @Test
    void thesameFindingProducesThesameFingerprintEveryTime() {
        assertThat(at(41, 63).format()).isEqualTo(at(41, 63).format());
    }

    @Test
    void anUnreadableFingerprintIsRefusedRatherThanTreatedAsNoDecision() {
        assertThatThrownBy(() -> FindingFingerprint.parse("nonsense"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a finding fingerprint");
        assertThatThrownBy(() -> FindingFingerprint.parse("abc:notanumber:63"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
