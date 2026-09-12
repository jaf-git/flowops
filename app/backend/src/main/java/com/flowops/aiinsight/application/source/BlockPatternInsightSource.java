package com.flowops.aiinsight.application.source;

import com.flowops.aiinsight.application.ViewInsightsUseCase.Detail;
import com.flowops.aiinsight.application.ViewInsightsUseCase.Evidence;
import com.flowops.aiinsight.application.ViewInsightsUseCase.Insight;
import com.flowops.aiinsight.application.port.TemplateHistoryPort.History;
import com.flowops.aiinsight.domain.BlockPatternDetection;
import com.flowops.aiinsight.domain.BlockPatternDetection.BlockPattern;
import com.flowops.aiinsight.domain.FindingKey;
import com.flowops.aiinsight.domain.InsightIdentity;
import com.flowops.aiinsight.domain.InsightKind;
import com.flowops.aiinsight.domain.SubjectType;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class BlockPatternInsightSource implements ProcessTemplateInsightSource {
    @Override
    public List<Insight> findIn(UUID templateId, History past) {
        return BlockPatternDetection.over(past.blocks()).stream()
                .map(pattern -> asInsight(templateId, past, pattern))
                .toList();
    }

    private Insight asInsight(UUID templateId, History past, BlockPattern pattern) {
        return new Insight(
                InsightIdentity.of(
                        InsightKind.BLOCK_PATTERN,
                        SubjectType.PROCESS_TEMPLATE,
                        templateId,
                        FindingKey.of(pattern.stepTitle(), pattern.reason())),
                past.templateName(),
                Evidence.of(
                        pattern.occurrences(),
                        past.completedRuns(),
                        past.firstCompletedAt(),
                        past.lastCompletedAt(),
                        List.copyOf(pattern.instances())),
                new Detail.BlockPattern(pattern.stepTitle(), pattern.reason()),
                null);
    }
}
