package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.model.WorkspaceId;
import com.flowops.workspace.domain.model.WorkspaceSettings;
import java.util.Optional;

public interface LoadWorkspaceSettingsPort {
    Optional<WorkspaceSettings> inForce(WorkspaceId workspaceId);
}
