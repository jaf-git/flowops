package com.flowops.workspace.domain.model;

import com.flowops.workspace.domain.exception.WorkspaceNameRequiredException;

public record WorkspaceName(String value) {
    public static final int MAXIMUM_LENGTH = 120;

    public WorkspaceName {
        if (value == null) {
            throw new WorkspaceNameRequiredException();
        }
        value = value.trim();
        if (value.isEmpty() || value.length() > MAXIMUM_LENGTH) {
            throw new WorkspaceNameRequiredException();
        }
    }
}
