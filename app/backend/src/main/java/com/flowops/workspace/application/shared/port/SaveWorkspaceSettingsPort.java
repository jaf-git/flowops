package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.model.WorkspaceSettings;

public interface SaveWorkspaceSettingsPort {
    void save(WorkspaceSettings settings);
}
