package com.flowops.aiinsight.application.port;

public interface AnalysisThresholdPort {
    int closureCoverageThresholdPercent();

    int templateIdleWindowDays();
}
