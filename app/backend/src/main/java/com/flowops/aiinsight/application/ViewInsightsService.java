package com.flowops.aiinsight.application;

import com.flowops.aiinsight.application.port.InsightDecisionPort;
import com.flowops.aiinsight.application.source.SubjectInsightReader;
import com.flowops.aiinsight.application.source.SubjectInsightRegistry;
import com.flowops.aiinsight.domain.DecidedFindings;
import com.flowops.aiinsight.domain.SubjectType;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewInsightsService implements ViewInsightsUseCase {
    private final SubjectInsightRegistry subjects;
    private final InsightDecisionPort decisions;

    public ViewInsightsService(SubjectInsightRegistry subjects, InsightDecisionPort decisions) {
        this.subjects = subjects;
        this.decisions = decisions;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Insight> forSubject(SubjectType subjectType, UUID subjectId) {
        SubjectInsightReader.Computed computed = subjects.read(subjectType, subjectId);
        DecidedFindings settled = DecidedFindings.from(decisions.decisionsFor(subjectType, subjectId));

        return computed.findings().stream()
                .filter(found -> !settled.suppresses(found.identity(), computed.fingerprint()))
                .toList();
    }
}
