package com.flowops.aiinsight.application.source;

import com.flowops.aiinsight.application.ViewInsightsUseCase.Detail;
import com.flowops.aiinsight.application.ViewInsightsUseCase.Evidence;
import com.flowops.aiinsight.application.ViewInsightsUseCase.Insight;
import com.flowops.aiinsight.application.port.TemplateHistoryPort.History;
import com.flowops.aiinsight.domain.FalseDependencyDetection;
import com.flowops.aiinsight.domain.FalseDependencyDetection.FalseDependency;
import com.flowops.aiinsight.domain.FindingKey;
import com.flowops.aiinsight.domain.InsightAction;
import com.flowops.aiinsight.domain.InsightIdentity;
import com.flowops.aiinsight.domain.InsightKind;
import com.flowops.aiinsight.domain.SubjectType;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FalseDependencyInsightSource implements ProcessTemplateInsightSource {
    @Override
    public List<Insight> findIn(UUID templateId, History past) {
        return FalseDependencyDetection.over(past.edgeObservations()).stream()
                .map(idle -> asInsight(templateId, past, idle))
                .toList();
    }

    private Insight asInsight(UUID templateId, History past, FalseDependency idle) {
        return new Insight(
                InsightIdentity.of(
                        InsightKind.FALSE_DEPENDENCY,
                        SubjectType.PROCESS_TEMPLATE,
                        templateId,
                        FindingKey.of(idle.dependentTitle(), idle.dependsOnTitle())),
                past.templateName(),
                Evidence.of(
                        idle.runsObserved(),
                        idle.runsObserved(),
                        past.coverage(),
                        past.firstCompletedAt(),
                        past.lastCompletedAt(),
                        List.copyOf(idle.instances())),
                new Detail.FalseDependency(idle.dependentTitle(), idle.dependsOnTitle()),
                new InsightAction.RemoveDependency(idle.dependentTitle(), idle.dependsOnTitle()));
    }
}
