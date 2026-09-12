package com.flowops.task.infrastructure.workspace;

import com.flowops.task.application.shared.port.WorkspaceSettingsPort;
import com.flowops.workspace.application.configureworkspace.ViewWorkspaceSettingsUseCase;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceSettingsAdapter implements WorkspaceSettingsPort {
    private final ViewWorkspaceSettingsUseCase viewWorkspaceSettingsUseCase;

    public WorkspaceSettingsAdapter(ViewWorkspaceSettingsUseCase viewWorkspaceSettingsUseCase) {
        this.viewWorkspaceSettingsUseCase = viewWorkspaceSettingsUseCase;
    }

    @Override
    public int atRiskWindowHours() {
        return viewWorkspaceSettingsUseCase.atRiskWindowHours();
    }
}
