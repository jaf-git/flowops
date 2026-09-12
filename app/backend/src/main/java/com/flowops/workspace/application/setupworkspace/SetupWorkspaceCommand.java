package com.flowops.workspace.application.setupworkspace;

import com.flowops.workspace.domain.enums.WorkspaceUse;

public record SetupWorkspaceCommand(String ownerName, String workspaceName, WorkspaceUse use, String timezone) {}
