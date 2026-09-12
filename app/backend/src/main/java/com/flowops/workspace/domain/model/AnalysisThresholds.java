package com.flowops.workspace.domain.model;

import com.flowops.workspace.domain.exception.AnalysisThresholdInvalidException;

public record AnalysisThresholds(int closureCoverageThresholdPercent, int templateIdleWindowDays) {
    public static AnalysisThresholds shippingDefaults() {
        return new AnalysisThresholds(90, 90);
    }

    public AnalysisThresholds {
        if (closureCoverageThresholdPercent < 1 || closureCoverageThresholdPercent > 100) {
            throw new AnalysisThresholdInvalidException("closureCoverageThresholdPercent");
        }

        if (templateIdleWindowDays <= 0) {
            throw new AnalysisThresholdInvalidException("templateIdleWindowDays");
        }
    }
}
