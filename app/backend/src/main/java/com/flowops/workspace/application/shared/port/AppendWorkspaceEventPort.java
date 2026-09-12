package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.event.WorkspaceEvent;

public interface AppendWorkspaceEventPort {
    void append(WorkspaceEvent event);
}
