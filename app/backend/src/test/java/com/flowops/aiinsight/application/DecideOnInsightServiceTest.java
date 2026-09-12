package com.flowops.aiinsight.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.aiinsight.application.ViewInsightsUseCase.Detail;
import com.flowops.aiinsight.application.ViewInsightsUseCase.Evidence;
import com.flowops.aiinsight.application.ViewInsightsUseCase.Insight;
import com.flowops.aiinsight.application.exception.InsightNoLongerHoldsException;
import com.flowops.aiinsight.application.exception.InsightNotActionableException;
import com.flowops.aiinsight.application.port.ApplyProcessChangePort;
import com.flowops.aiinsight.application.port.ApplyTemplateChangePort;
import com.flowops.aiinsight.application.port.InsightDecisionPort;
import com.flowops.aiinsight.application.source.SubjectInsightReader;
import com.flowops.aiinsight.application.source.SubjectInsightRegistry;
import com.flowops.aiinsight.domain.DecisionOutcome;
import com.flowops.aiinsight.domain.FindingKey;
import com.flowops.aiinsight.domain.InsightAction;
import com.flowops.aiinsight.domain.InsightIdentity;
import com.flowops.aiinsight.domain.InsightKind;
import com.flowops.aiinsight.domain.RecordedDecision;
import com.flowops.aiinsight.domain.SubjectFingerprint;
import com.flowops.aiinsight.domain.SubjectType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("AI-INSIGHT-ACT-ON-INSIGHT-01")
class DecideOnInsightServiceTest {
    private static final UUID TEMPLATE = UUID.randomUUID();
    private static final UUID DECIDER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");

    private final RecordingProcessChanges processChanges = new RecordingProcessChanges();
    private final RecordingTemplateChanges templateChanges = new RecordingTemplateChanges();
    private final RecordingDecisions decisions = new RecordingDecisions();

    private DecideOnInsightService serviceFinding(Insight... found) {
        SubjectInsightRegistry registry = new SubjectInsightRegistry(
                List.of(readerOf(SubjectType.PROCESS_TEMPLATE, found), readerOf(SubjectType.TASK_TEMPLATE, found)));

        return new DecideOnInsightService(
                registry,
                decisions,
                processChanges,
                templateChanges,
                () -> Optional.of(DECIDER),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static SubjectInsightReader readerOf(SubjectType subject, Insight... found) {
        List<Insight> findings = List.of(found).stream()
                .filter(one -> one.identity().subjectType() == subject)
                .toList();
        return new SubjectInsightReader() {
            @Override
            public SubjectType subject() {
                return subject;
            }

            @Override
            public Computed read(UUID subjectId) {
                return new Computed(findings, SubjectFingerprint.over(List.of("step:Proposal")));
            }
        };
    }

    private static Insight missingStep(String title, int position) {
        return new Insight(
                InsightIdentity.of(
                        InsightKind.MISSING_STEP,
                        SubjectType.PROCESS_TEMPLATE,
                        TEMPLATE,
                        FindingKey.of(title, position)),
                "Client onboarding",
                Evidence.of(8, 12, NOW, NOW, List.of()),
                new Detail.MissingStep(title, "Proposal", "Contract", position),
                new InsightAction.InsertStep(title, position));
    }

    private static Insight slowStep() {
        return new Insight(
                InsightIdentity.of(
                        InsightKind.SLOW_STEP,
                        SubjectType.PROCESS_TEMPLATE,
                        TEMPLATE,
                        FindingKey.of("client sign off")),
                "Client onboarding",
                Evidence.of(9, 9, NOW, NOW, List.of()),
                new Detail.SlowStep("Client sign-off", new ViewInsightsUseCase.Phases(1, 2, 3, 4)),
                null);
    }

    @Test
    void asksForTheStepAtThePositionTheFindingNamed() {
        Insight finding = missingStep("Send reminder", 2);

        serviceFinding(finding).apply(finding.identity());

        assertThat(processChanges.insertions)
                .as("appending it instead would be a different change made under the name of the one a"
                        + " person read")
                .containsExactly(new Insertion(TEMPLATE, 2, "Send reminder"));
    }

    @Test
    void asksForTheEdgeByBothItsEnds() {
        Insight finding = new Insight(
                InsightIdentity.of(
                        InsightKind.FALSE_DEPENDENCY,
                        SubjectType.PROCESS_TEMPLATE,
                        TEMPLATE,
                        FindingKey.of("Prepare materials", "Book room")),
                "Client onboarding",
                Evidence.of(9, 9, NOW, NOW, List.of()),
                new Detail.FalseDependency("Prepare materials", "Book room"),
                new InsightAction.RemoveDependency("Prepare materials", "Book room"));

        serviceFinding(finding).apply(finding.identity());

        assertThat(processChanges.removals).containsExactly(new Removal(TEMPLATE, "Prepare materials", "Book room"));
    }

    @Test
    void appliesTheFindingThatWasNamedAndNotItsNeighbour() {
        Insight second = missingStep("Chase the client", 5);

        serviceFinding(missingStep("Send reminder", 2), second).apply(second.identity());

        assertThat(processChanges.insertions).containsExactly(new Insertion(TEMPLATE, 5, "Chase the client"));
    }

    @Test
    void refusesAFindingItNoLongerComputes() {
        InsightIdentity stale = missingStep("Send reminder", 2).identity();
        DecideOnInsightService service = serviceFinding();

        assertThatThrownBy(() -> service.apply(stale)).isInstanceOf(InsightNoLongerHoldsException.class);
        assertThat(processChanges.insertions).isEmpty();
        assertThat(decisions.written)
                .as("nothing is recorded about a change that did not happen")
                .isEmpty();
    }

    @Test
    void refusesToApplyAFindingThatProposesNothing() {
        Insight informational = slowStep();
        DecideOnInsightService service = serviceFinding(informational);

        assertThatThrownBy(() -> service.apply(informational.identity()))
                .isInstanceOf(InsightNotActionableException.class);
        assertThat(processChanges.insertions).isEmpty();
    }

    @Test
    void dismissesAFindingThatProposesNothing() {
        Insight informational = slowStep();

        serviceFinding(informational).dismiss(informational.identity());

        assertThat(decisions.written).singleElement().satisfies(written -> {
            assertThat(written.outcome()).isEqualTo(DecisionOutcome.DISMISSED);
            assertThat(written.identity()).isEqualTo(informational.identity());
        });
    }

    @Test
    void changesNothingAnywhereWhenDismissing() {
        Insight finding = missingStep("Send reminder", 2);

        serviceFinding(finding).dismiss(finding.identity());

        assertThat(processChanges.insertions).isEmpty();
        assertThat(processChanges.removals).isEmpty();
        assertThat(templateChanges.retired).isEmpty();
    }

    @Test
    void recordsNothingWhenTheSubjectFeatureRefuses() {
        Insight finding = missingStep("Send reminder", 2);
        processChanges.refuseWith = new IllegalStateException("that step already exists");
        DecideOnInsightService service = serviceFinding(finding);

        assertThatThrownBy(() -> service.apply(finding.identity())).isInstanceOf(IllegalStateException.class);
        assertThat(decisions.written).isEmpty();
    }

    private record Insertion(UUID templateId, int position, String title) {}

    private record Removal(UUID templateId, String dependent, String dependsOn) {}

    private static final class RecordingProcessChanges implements ApplyProcessChangePort {
        private final List<Insertion> insertions = new ArrayList<>();
        private final List<Removal> removals = new ArrayList<>();
        private RuntimeException refuseWith;

        @Override
        public void insertStep(UUID templateId, int position, String title) {
            if (refuseWith != null) {
                throw refuseWith;
            }
            insertions.add(new Insertion(templateId, position, title));
        }

        @Override
        public void removeDependency(UUID templateId, String dependentTitle, String dependsOnTitle) {
            if (refuseWith != null) {
                throw refuseWith;
            }
            removals.add(new Removal(templateId, dependentTitle, dependsOnTitle));
        }
    }

    private static final class RecordingTemplateChanges implements ApplyTemplateChangePort {
        private final List<UUID> retired = new ArrayList<>();
        private final List<Long> corrected = new ArrayList<>();

        @Override
        public void retire(UUID templateId) {
            retired.add(templateId);
        }

        @Override
        public void correctEstimate(UUID templateId, long medianWorkMs) {
            corrected.add(medianWorkMs);
        }
    }

    private static final class RecordingDecisions implements InsightDecisionPort {
        private final List<RecordedDecision> written = new ArrayList<>();

        @Override
        public void record(
                InsightIdentity identity,
                DecisionOutcome outcome,
                SubjectFingerprint fingerprint,
                UUID deciderUserId,
                Instant decidedAt) {
            written.add(new RecordedDecision(identity, outcome, fingerprint.value()));
        }

        @Override
        public List<RecordedDecision> decisionsFor(SubjectType subjectType, UUID subjectId) {
            return List.copyOf(written);
        }
    }
}
