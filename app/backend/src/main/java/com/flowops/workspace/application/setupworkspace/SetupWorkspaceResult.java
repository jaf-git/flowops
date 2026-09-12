package com.flowops.workspace.application.setupworkspace;

import com.flowops.workspace.domain.enums.WorkspaceUse;
import com.flowops.workspace.domain.model.Timezone;
import com.flowops.workspace.domain.model.WorkspaceId;
import com.flowops.workspace.domain.model.WorkspaceName;

public record SetupWorkspaceResult(
        WorkspaceId workspaceId, WorkspaceName name, WorkspaceUse use, Timezone timezone, String landingTarget) {}
