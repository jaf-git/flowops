package com.flowops.workspace.application.configureworkspace;

public interface ConfigureWorkspaceUseCase {
    WorkspaceSettingsView execute(ConfigureWorkspaceCommand command);
}
