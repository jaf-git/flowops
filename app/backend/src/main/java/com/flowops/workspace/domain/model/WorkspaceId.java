package com.flowops.workspace.domain.model;

import java.util.Objects;
import java.util.UUID;

public record WorkspaceId(UUID value) {
    public WorkspaceId {
        Objects.requireNonNull(value, "a workspace identity is required");
    }

    public static WorkspaceId of(UUID value) {
        return new WorkspaceId(value);
    }
}
