package com.flowops.aiinsight.application;

import com.flowops.aiinsight.application.exception.InsightNoLongerHoldsException;
import com.flowops.aiinsight.application.exception.InsightNotActionableException;
import com.flowops.aiinsight.application.port.ApplyProcessChangePort;
import com.flowops.aiinsight.application.port.ApplyTemplateChangePort;
import com.flowops.aiinsight.application.port.IdentifyCallerPort;
import com.flowops.aiinsight.application.port.InsightDecisionPort;
import com.flowops.aiinsight.application.source.SubjectInsightReader;
import com.flowops.aiinsight.application.source.SubjectInsightRegistry;
import com.flowops.aiinsight.domain.DecisionOutcome;
import com.flowops.aiinsight.domain.InsightAction;
import com.flowops.aiinsight.domain.InsightIdentity;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DecideOnInsightService implements DecideOnInsightUseCase {
    private final SubjectInsightRegistry subjects;
    private final InsightDecisionPort decisions;
    private final ApplyProcessChangePort processChanges;
    private final ApplyTemplateChangePort templateChanges;
    private final IdentifyCallerPort caller;
    private final Clock clock;

    public DecideOnInsightService(
            SubjectInsightRegistry subjects,
            InsightDecisionPort decisions,
            ApplyProcessChangePort processChanges,
            ApplyTemplateChangePort templateChanges,
            IdentifyCallerPort caller,
            Clock clock) {
        this.subjects = subjects;
        this.decisions = decisions;
        this.processChanges = processChanges;
        this.templateChanges = templateChanges;
        this.caller = caller;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void apply(InsightIdentity identity) {
        SubjectInsightReader.Computed computed = subjects.read(identity.subjectType(), identity.subjectId());
        ViewInsightsUseCase.Insight finding = locate(computed, identity);

        if (!finding.isActionable()) {
            throw new InsightNotActionableException(identity.kind());
        }

        perform(finding.action(), identity.subjectId());
        record(identity, DecisionOutcome.APPLIED, computed);
    }

    @Override
    @Transactional
    public void dismiss(InsightIdentity identity) {
        SubjectInsightReader.Computed computed = subjects.read(identity.subjectType(), identity.subjectId());
        locate(computed, identity);

        record(identity, DecisionOutcome.DISMISSED, computed);
    }

    private ViewInsightsUseCase.Insight locate(SubjectInsightReader.Computed computed, InsightIdentity identity) {
        return computed.findings().stream()
                .filter(found -> found.identity().equals(identity))
                .findFirst()
                .orElseThrow(() -> new InsightNoLongerHoldsException(identity));
    }

    private void perform(InsightAction action, UUID subjectId) {
        switch (action) {
            case InsightAction.InsertStep insert -> processChanges.insertStep(
                    subjectId, insert.position(), insert.title());
            case InsightAction.RemoveDependency remove -> processChanges.removeDependency(
                    subjectId, remove.dependentTitle(), remove.dependsOnTitle());
            case InsightAction.RetireTemplate ignored -> templateChanges.retire(subjectId);
            case InsightAction.UpdateEstimate correction -> templateChanges.correctEstimate(
                    subjectId, correction.medianWorkMs());
        }
    }

    private void record(InsightIdentity identity, DecisionOutcome outcome, SubjectInsightReader.Computed computed) {
        UUID decider = caller.currentCaller()
                .orElseThrow(() -> new IllegalStateException("a decision is made by a person, and there is none"));

        decisions.record(identity, outcome, computed.fingerprint(), decider, clock.instant());
    }
}
