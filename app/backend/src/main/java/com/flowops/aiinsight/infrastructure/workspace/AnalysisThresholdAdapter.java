package com.flowops.aiinsight.infrastructure.workspace;

import com.flowops.aiinsight.application.port.AnalysisThresholdPort;
import com.flowops.workspace.application.configureworkspace.ViewWorkspaceSettingsUseCase;
import org.springframework.stereotype.Component;

@Component
public class AnalysisThresholdAdapter implements AnalysisThresholdPort {
    private final ViewWorkspaceSettingsUseCase settings;

    public AnalysisThresholdAdapter(ViewWorkspaceSettingsUseCase settings) {
        this.settings = settings;
    }

    @Override
    public int closureCoverageThresholdPercent() {
        return settings.analysisThresholds().closureCoverageThresholdPercent();
    }

    @Override
    public int templateIdleWindowDays() {
        return settings.analysisThresholds().templateIdleWindowDays();
    }
}
