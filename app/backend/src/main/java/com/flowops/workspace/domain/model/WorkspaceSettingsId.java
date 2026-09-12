package com.flowops.workspace.domain.model;

import java.util.Objects;
import java.util.UUID;

public record WorkspaceSettingsId(UUID value) {
    public WorkspaceSettingsId {
        Objects.requireNonNull(value, "a settings identity is required");
    }

    public static WorkspaceSettingsId generate() {
        return new WorkspaceSettingsId(UUID.randomUUID());
    }

    public static WorkspaceSettingsId of(UUID value) {
        return new WorkspaceSettingsId(value);
    }
}
