package com.flowops.aiinsight.application.source;

import com.flowops.aiinsight.application.ViewInsightsUseCase.Detail;
import com.flowops.aiinsight.application.ViewInsightsUseCase.Evidence;
import com.flowops.aiinsight.application.ViewInsightsUseCase.Insight;
import com.flowops.aiinsight.application.port.AnalysisThresholdPort;
import com.flowops.aiinsight.application.port.TemplateUsagePort;
import com.flowops.aiinsight.domain.FindingKey;
import com.flowops.aiinsight.domain.InsightAction;
import com.flowops.aiinsight.domain.InsightIdentity;
import com.flowops.aiinsight.domain.InsightKind;
import com.flowops.aiinsight.domain.SubjectType;
import com.flowops.aiinsight.domain.UnusedTemplateDetection;
import com.flowops.aiinsight.domain.UnusedTemplateDetection.UnusedTemplate;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UnusedTemplateInsightSource implements TaskTemplateInsightSource {
    private final Clock clock;
    private final AnalysisThresholdPort thresholds;

    public UnusedTemplateInsightSource(Clock clock, AnalysisThresholdPort thresholds) {
        this.clock = clock;
        this.thresholds = thresholds;
    }

    @Override
    public List<Insight> findIn(UUID templateId, TemplateUsagePort.Usage usage) {
        return UnusedTemplateDetection.over(
                        List.of(usage.usage()), clock.instant(), thresholds.templateIdleWindowDays())
                .stream()
                .map(stale -> asInsight(templateId, stale))
                .toList();
    }

    private Insight asInsight(UUID templateId, UnusedTemplate stale) {
        return new Insight(
                InsightIdentity.of(
                        InsightKind.UNUSED_TEMPLATE, SubjectType.TASK_TEMPLATE, templateId, FindingKey.of("unused")),
                stale.name(),
                Evidence.of(stale.timesUsed(), stale.windowDays(), stale.lastUsedAt(), null, List.of()),
                new Detail.UnusedTemplate(
                        stale.daysSinceLastUse(), stale.timesUsed(), stale.windowDays(), stale.expectedIntervalDays()),
                new InsightAction.RetireTemplate());
    }
}
