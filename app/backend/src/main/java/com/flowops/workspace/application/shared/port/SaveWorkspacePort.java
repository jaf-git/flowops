package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.model.Workspace;

public interface SaveWorkspacePort {
    void save(Workspace workspace);
}
