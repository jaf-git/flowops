package com.flowops.aiinsight.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.aiinsight.domain.MissingStepDetection.AttachedStep;
import com.flowops.aiinsight.domain.MissingStepDetection.MissingStep;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("AI-INSIGHT-MISSING-STEP-01")
class MissingStepDetectionTest {
    @Test
    void countsAStepThatRecursAtTheSamePositionAcrossRuns() {
        List<AttachedStep> attached = new ArrayList<>();
        for (int run = 0; run < 5; run++) {
            attached.add(new AttachedStep(UUID.randomUUID(), "Chase the supplier", 2));
        }

        List<MissingStep> found = MissingStepDetection.over(attached, 9);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).title()).isEqualTo("Chase the supplier");
        assertThat(found.get(0).runsThatAddedIt()).isEqualTo(5);
        assertThat(found.get(0).position()).isEqualTo(2);
    }

    @Test
    void saysNothingBelowThreeOccurrences() {
        List<AttachedStep> twice = List.of(
                new AttachedStep(UUID.randomUUID(), "Chase the supplier", 2),
                new AttachedStep(UUID.randomUUID(), "Chase the supplier", 2));

        assertThat(MissingStepDetection.over(twice, 8)).isEmpty();
    }

    @Test
    void saysNothingWhenTheTemplateHasFewerThanThreeCompletedRuns() {
        List<AttachedStep> attached = List.of(
                new AttachedStep(UUID.randomUUID(), "Chase the supplier", 2),
                new AttachedStep(UUID.randomUUID(), "Chase the supplier", 2),
                new AttachedStep(UUID.randomUUID(), "Chase the supplier", 2));

        assertThat(MissingStepDetection.over(attached, 2)).isEmpty();
    }

    @Test
    void saysNothingWhenTheSameTitleIsScatteredAcrossPositions() {
        List<AttachedStep> scattered = List.of(
                new AttachedStep(UUID.randomUUID(), "Chase the supplier", 1),
                new AttachedStep(UUID.randomUUID(), "Chase the supplier", 1),
                new AttachedStep(UUID.randomUUID(), "Chase the supplier", 2),
                new AttachedStep(UUID.randomUUID(), "Chase the supplier", 2),
                new AttachedStep(UUID.randomUUID(), "Chase the supplier", 3),
                new AttachedStep(UUID.randomUUID(), "Chase the supplier", 3));

        assertThat(MissingStepDetection.over(scattered, 9)).isEmpty();
    }

    @Test
    void groupsTitlesDifferingOnlyInCaseOrPunctuation() {
        List<AttachedStep> spelled = List.of(
                new AttachedStep(UUID.randomUUID(), "Send reminder", 2),
                new AttachedStep(UUID.randomUUID(), "send reminder", 2),
                new AttachedStep(UUID.randomUUID(), "Send  reminder.", 2));

        List<MissingStep> found = MissingStepDetection.over(spelled, 5);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).runsThatAddedIt()).isEqualTo(3);
        assertThat(found.get(0).title())
                .as("reported in the words somebody actually typed, not in the normalised form")
                .isEqualTo("Send reminder");
    }

    @Test
    void doesNotGroupTitlesThatOnlyAPersonWouldCallTheSame() {
        List<AttachedStep> similar = List.of(
                new AttachedStep(UUID.randomUUID(), "Send reminder", 2),
                new AttachedStep(UUID.randomUUID(), "Send a reminder email", 2),
                new AttachedStep(UUID.randomUUID(), "Chase the client", 2));

        assertThat(MissingStepDetection.over(similar, 7)).isEmpty();
    }

    @Test
    void countsRunsRatherThanSteps() {
        UUID oneBusyRun = UUID.randomUUID();
        List<AttachedStep> attached = List.of(
                new AttachedStep(oneBusyRun, "Chase the supplier", 2),
                new AttachedStep(oneBusyRun, "Chase the supplier", 2),
                new AttachedStep(oneBusyRun, "Chase the supplier", 2),
                new AttachedStep(UUID.randomUUID(), "Chase the supplier", 2));

        assertThat(MissingStepDetection.over(attached, 6))
                .as("one run that added it three times is one run, not three — the sentence says"
                        + " 'N of M runs' and a run cannot be two of them")
                .isEmpty();
    }

    @Test
    void ordersSeveralFindingsByHowOftenEachHappens() {
        List<AttachedStep> attached = new ArrayList<>();
        for (int run = 0; run < 3; run++) {
            attached.add(new AttachedStep(UUID.randomUUID(), "Book the room", 1));
        }
        for (int run = 0; run < 6; run++) {
            attached.add(new AttachedStep(UUID.randomUUID(), "Chase the supplier", 4));
        }

        List<MissingStep> found = MissingStepDetection.over(attached, 10);

        assertThat(found).hasSize(2);
        assertThat(found.get(0).title()).isEqualTo("Chase the supplier");
        assertThat(found.get(0).runsThatAddedIt()).isEqualTo(6);
        assertThat(found.get(1).title()).isEqualTo("Book the room");
    }

    @Test
    void saysNothingAtAllWhenNothingWasEverAttached() {
        assertThat(MissingStepDetection.over(List.of(), 40)).isEmpty();
    }

    @Test
    void carriesTheRunsItCountedSoExplainCanScopeAnExportToExactlyThem() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        List<AttachedStep> attached = List.of(
                new AttachedStep(first, "Chase the supplier", 2),
                new AttachedStep(second, "Chase the supplier", 2),
                new AttachedStep(third, "Chase the supplier", 2));

        assertThat(MissingStepDetection.over(attached, 5).get(0).instances())
                .as("a claim a person can check is a claim that names the records behind it")
                .containsExactlyInAnyOrder(first, second, third);
    }
}
