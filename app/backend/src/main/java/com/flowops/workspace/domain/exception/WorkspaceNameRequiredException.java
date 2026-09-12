package com.flowops.workspace.domain.exception;

public class WorkspaceNameRequiredException extends RuntimeException {
    public WorkspaceNameRequiredException() {
        super("a workspace name is required");
    }
}
