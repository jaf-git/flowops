package com.flowops.aiinsight.application.source;

import com.flowops.aiinsight.application.ViewInsightsUseCase.Detail;
import com.flowops.aiinsight.application.ViewInsightsUseCase.Evidence;
import com.flowops.aiinsight.application.ViewInsightsUseCase.Insight;
import com.flowops.aiinsight.application.port.TemplateHistoryPort.History;
import com.flowops.aiinsight.domain.FindingKey;
import com.flowops.aiinsight.domain.InsightAction;
import com.flowops.aiinsight.domain.InsightIdentity;
import com.flowops.aiinsight.domain.InsightKind;
import com.flowops.aiinsight.domain.MissingStepDetection;
import com.flowops.aiinsight.domain.MissingStepDetection.MissingStep;
import com.flowops.aiinsight.domain.SubjectType;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MissingStepInsightSource implements ProcessTemplateInsightSource {
    @Override
    public List<Insight> findIn(UUID templateId, History past) {
        return MissingStepDetection.over(past.attachedSteps(), past.completedRuns()).stream()
                .map(missing -> asInsight(templateId, past, missing))
                .toList();
    }

    private Insight asInsight(UUID templateId, History past, MissingStep missing) {
        return new Insight(
                InsightIdentity.of(
                        InsightKind.MISSING_STEP,
                        SubjectType.PROCESS_TEMPLATE,
                        templateId,
                        FindingKey.of(missing.title(), missing.position())),
                past.templateName(),
                Evidence.of(
                        missing.runsThatAddedIt(),
                        past.completedRuns(),
                        past.coverage(),
                        past.firstCompletedAt(),
                        past.lastCompletedAt(),
                        List.copyOf(missing.instances())),
                new Detail.MissingStep(
                        missing.title(),
                        past.plannedStepBefore(missing.position()),
                        past.plannedStepAfter(missing.position()),
                        missing.position()),
                new InsightAction.InsertStep(missing.title(), missing.position()));
    }
}
