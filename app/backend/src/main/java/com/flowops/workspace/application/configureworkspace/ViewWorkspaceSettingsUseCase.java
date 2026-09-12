package com.flowops.workspace.application.configureworkspace;

public interface ViewWorkspaceSettingsUseCase {
    WorkspaceSettingsView execute();

    int atRiskWindowHours();

    AnalysisThresholdsView analysisThresholds();

    record AnalysisThresholdsView(int closureCoverageThresholdPercent, int templateIdleWindowDays) {}
}
