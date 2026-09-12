package com.flowops.aiinsight.application.source;

import com.flowops.aiinsight.application.port.AnalysisThresholdPort;
import com.flowops.aiinsight.application.port.TemplateUsagePort;
import com.flowops.aiinsight.domain.SubjectFingerprint;
import com.flowops.aiinsight.domain.SubjectType;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TaskTemplateInsightReader implements SubjectInsightReader {
    private final TemplateUsagePort usage;
    private final AnalysisThresholdPort thresholds;
    private final List<TaskTemplateInsightSource> sources;

    public TaskTemplateInsightReader(
            TemplateUsagePort usage, AnalysisThresholdPort thresholds, List<TaskTemplateInsightSource> sources) {
        this.usage = usage;
        this.thresholds = thresholds;
        this.sources = List.copyOf(sources);
    }

    @Override
    public SubjectType subject() {
        return SubjectType.TASK_TEMPLATE;
    }

    @Override
    public Computed read(UUID templateId) {
        return usage.of(templateId)
                .map(found -> found.withCoverageResolvedAt(thresholds.closureCoverageThresholdPercent()))
                .map(standing -> new Computed(
                        sources.stream()
                                .flatMap(source -> source.findIn(templateId, standing).stream())
                                .toList(),
                        SubjectFingerprint.over(standing.fingerprintParts())))
                .orElseGet(Computed::nothing);
    }
}
