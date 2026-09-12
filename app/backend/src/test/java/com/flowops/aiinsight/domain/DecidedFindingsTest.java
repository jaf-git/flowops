package com.flowops.aiinsight.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("AI-INSIGHT-ACT-ON-INSIGHT-01")
class DecidedFindingsTest {
    private static final UUID TEMPLATE = UUID.randomUUID();
    private static final SubjectFingerprint UNCHANGED = SubjectFingerprint.over(List.of("step:Proposal"));
    private static final SubjectFingerprint REAUTHORED =
            SubjectFingerprint.over(List.of("step:Proposal", "step:Contract"));

    private static InsightIdentity aMissingStep(String title) {
        return InsightIdentity.of(
                InsightKind.MISSING_STEP, SubjectType.PROCESS_TEMPLATE, TEMPLATE, FindingKey.of(title, 2));
    }

    private static RecordedDecision dismissed(InsightIdentity identity, SubjectFingerprint shape) {
        return new RecordedDecision(identity, DecisionOutcome.DISMISSED, shape.value());
    }

    @Test
    void saysNothingAboutAFindingNobodyHasDecided() {
        DecidedFindings settled = DecidedFindings.none();

        assertThat(settled.suppresses(aMissingStep("Send reminder"), UNCHANGED)).isFalse();
    }

    @Test
    void suppressesADismissedFindingWhileTheSubjectIsUnchanged() {
        InsightIdentity finding = aMissingStep("Send reminder");
        DecidedFindings settled = DecidedFindings.from(List.of(dismissed(finding, UNCHANGED)));

        assertThat(settled.suppresses(finding, UNCHANGED)).isTrue();
    }

    @Test
    void keepsADismissalWhateverTheEvidenceDoes() {
        InsightIdentity finding = aMissingStep("Send reminder");
        DecidedFindings settled = DecidedFindings.from(List.of(dismissed(finding, UNCHANGED)));

        SubjectFingerprint afterNineMoreRuns = SubjectFingerprint.over(List.of("step:Proposal"));

        assertThat(settled.suppresses(finding, afterNineMoreRuns))
                .as("the shape did not change, so neither did the judgement")
                .isTrue();
    }

    @Test
    void letsADismissedFindingReturnOnceTheSubjectIsReAuthored() {
        InsightIdentity finding = aMissingStep("Send reminder");
        DecidedFindings settled = DecidedFindings.from(List.of(dismissed(finding, UNCHANGED)));

        assertThat(settled.suppresses(finding, REAUTHORED))
                .as("the person judged the process they had, and it is not the process they have")
                .isFalse();
    }

    @Test
    void suppressesAnAppliedFindingEvenThoughApplyingItChangedTheSubject() {
        InsightIdentity finding = aMissingStep("Send reminder");
        DecidedFindings settled = DecidedFindings.from(
                List.of(new RecordedDecision(finding, DecisionOutcome.APPLIED, UNCHANGED.value())));

        assertThat(settled.suppresses(finding, REAUTHORED)).isTrue();
    }

    @Test
    void settlesOneFindingWithoutSilencingItsNeighbours() {
        InsightIdentity decided = aMissingStep("Send reminder");
        InsightIdentity untouched = aMissingStep("Chase the client");
        DecidedFindings settled = DecidedFindings.from(List.of(dismissed(decided, UNCHANGED)));

        assertThat(settled.suppresses(untouched, UNCHANGED))
                .as("one subject produces several findings of one kind, and a judgement about one is not"
                        + " a judgement about the others")
                .isFalse();
    }

    @Test
    void readsTheNewestJudgementAboutAFinding() {
        InsightIdentity finding = aMissingStep("Send reminder");
        DecidedFindings settled =
                DecidedFindings.from(List.of(dismissed(finding, REAUTHORED), dismissed(finding, UNCHANGED)));

        assertThat(settled.suppresses(finding, REAUTHORED))
                .as("dismissed, revived by an edit, dismissed again — the third row is the one in force")
                .isTrue();
    }

    @Test
    void doesNotSuppressOnAShapeItCouldNotRead() {
        InsightIdentity finding = aMissingStep("Send reminder");
        SubjectFingerprint unreadable = SubjectFingerprint.over(List.of());
        DecidedFindings settled = DecidedFindings.from(List.of(dismissed(finding, unreadable)));

        assertThat(settled.suppresses(finding, unreadable)).isFalse();
    }
}
