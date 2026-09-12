package com.flowops.aiinsight.application.source;

import com.flowops.aiinsight.application.ViewInsightsUseCase.Insight;
import com.flowops.aiinsight.application.port.AnalysisThresholdPort;
import com.flowops.aiinsight.application.port.TemplateHistoryPort;
import com.flowops.aiinsight.domain.SubjectFingerprint;
import com.flowops.aiinsight.domain.SubjectType;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProcessTemplateInsightReader implements SubjectInsightReader {
    private final TemplateHistoryPort history;
    private final AnalysisThresholdPort thresholds;
    private final List<ProcessTemplateInsightSource> sources;

    public ProcessTemplateInsightReader(
            TemplateHistoryPort history, AnalysisThresholdPort thresholds, List<ProcessTemplateInsightSource> sources) {
        this.history = history;
        this.thresholds = thresholds;
        this.sources = List.copyOf(sources);
    }

    @Override
    public SubjectType subject() {
        return SubjectType.PROCESS_TEMPLATE;
    }

    @Override
    public Computed read(UUID templateId) {
        TemplateHistoryPort.History past =
                history.of(templateId).withCoverageResolvedAt(thresholds.closureCoverageThresholdPercent());

        List<Insight> findings = sources.stream()
                .flatMap(source -> source.findIn(templateId, past).stream())
                .toList();

        return new Computed(findings, SubjectFingerprint.over(past.fingerprintParts()));
    }
}
