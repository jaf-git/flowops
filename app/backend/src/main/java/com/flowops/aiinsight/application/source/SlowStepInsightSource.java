package com.flowops.aiinsight.application.source;

import com.flowops.aiinsight.application.ViewInsightsUseCase.Detail;
import com.flowops.aiinsight.application.ViewInsightsUseCase.Evidence;
import com.flowops.aiinsight.application.ViewInsightsUseCase.Insight;
import com.flowops.aiinsight.application.ViewInsightsUseCase.Phases;
import com.flowops.aiinsight.application.port.TemplateHistoryPort.History;
import com.flowops.aiinsight.domain.FindingKey;
import com.flowops.aiinsight.domain.InsightIdentity;
import com.flowops.aiinsight.domain.InsightKind;
import com.flowops.aiinsight.domain.SlowStepDetection;
import com.flowops.aiinsight.domain.SubjectType;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SlowStepInsightSource implements ProcessTemplateInsightSource {
    @Override
    public List<Insight> findIn(UUID templateId, History past) {
        return SlowStepDetection.over(past.stepDurations(), past.completedRuns())
                .map(slowest -> new Insight(
                        InsightIdentity.of(
                                InsightKind.SLOW_STEP,
                                SubjectType.PROCESS_TEMPLATE,
                                templateId,
                                FindingKey.of(slowest.title())),
                        past.templateName(),
                        Evidence.of(
                                slowest.stepsCounted(),
                                past.completedRuns(),
                                past.coverage(),
                                past.firstCompletedAt(),
                                past.lastCompletedAt(),
                                List.of()),
                        new Detail.SlowStep(
                                slowest.title(),
                                new Phases(
                                        slowest.medianWorkMs(),
                                        slowest.medianBlockedMs(),
                                        slowest.medianWaitingMs(),
                                        slowest.medianReviewMs())),
                        null))
                .map(List::of)
                .orElseGet(List::of);
    }
}
