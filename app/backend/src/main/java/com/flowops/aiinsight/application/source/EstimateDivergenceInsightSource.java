package com.flowops.aiinsight.application.source;

import com.flowops.aiinsight.application.ViewInsightsUseCase.Detail;
import com.flowops.aiinsight.application.ViewInsightsUseCase.Evidence;
import com.flowops.aiinsight.application.ViewInsightsUseCase.Insight;
import com.flowops.aiinsight.application.port.TemplateUsagePort;
import com.flowops.aiinsight.domain.EstimateDivergenceDetection;
import com.flowops.aiinsight.domain.EstimateDivergenceDetection.Divergence;
import com.flowops.aiinsight.domain.FindingKey;
import com.flowops.aiinsight.domain.InsightAction;
import com.flowops.aiinsight.domain.InsightIdentity;
import com.flowops.aiinsight.domain.InsightKind;
import com.flowops.aiinsight.domain.SubjectType;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class EstimateDivergenceInsightSource implements TaskTemplateInsightSource {
    @Override
    public List<Insight> findIn(UUID templateId, TemplateUsagePort.Usage standing) {
        return EstimateDivergenceDetection.in(standing.closedTasks(), standing.currentEstimateMs())
                .map(divergence -> asInsight(templateId, standing, divergence))
                .map(List::of)
                .orElseGet(List::of);
    }

    private Insight asInsight(UUID templateId, TemplateUsagePort.Usage standing, Divergence divergence) {
        return new Insight(
                InsightIdentity.of(
                        InsightKind.ESTIMATE_DIVERGENCE,
                        SubjectType.TASK_TEMPLATE,
                        templateId,
                        FindingKey.of("estimate")),
                standing.usage().name(),
                Evidence.of(
                        standing.closedTasks().size(),
                        standing.closedTasks().size() + standing.neverClosedTasks(),
                        standing.coverage(),
                        firstUse(standing),
                        standing.usage().lastUsedAt(),
                        List.of()),
                new Detail.EstimateDivergence(
                        divergence.estimatedMs(),
                        divergence.medianWorkMs(),
                        divergence.fastestMiddleMs(),
                        divergence.slowestMiddleMs(),
                        divergence.spreadIsWide()),
                new InsightAction.UpdateEstimate(divergence.medianWorkMs()));
    }

    private static java.time.Instant firstUse(TemplateUsagePort.Usage standing) {
        return standing.usage().usedAt().stream().findFirst().orElse(null);
    }
}
