package com.flowops.aiinsight.application.port;

import com.flowops.aiinsight.domain.ClosureCoverage;
import com.flowops.aiinsight.domain.EstimateDivergenceDetection.ClosedTask;
import com.flowops.aiinsight.domain.UnusedTemplateDetection.TemplateUsage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TemplateUsagePort {
    Optional<Usage> of(UUID templateId);

    record Usage(
            TemplateUsage usage,
            List<ClosedTask> closedTasks,
            int neverClosedTasks,
            Long currentEstimateMs,
            ClosureCoverage coverage,
            List<String> fingerprintParts) {
        public Usage withCoverageResolvedAt(int thresholdPercent) {
            return new Usage(
                    usage,
                    closedTasks,
                    neverClosedTasks,
                    currentEstimateMs,
                    ClosureCoverage.materialIn(
                            closedTasks.size(), closedTasks.size() + neverClosedTasks, thresholdPercent),
                    fingerprintParts);
        }
    }
}
