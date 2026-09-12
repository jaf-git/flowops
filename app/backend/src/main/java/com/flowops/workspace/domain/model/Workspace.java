package com.flowops.workspace.domain.model;

import com.flowops.workspace.domain.enums.WorkspaceUse;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class Workspace {
    private final WorkspaceId id;
    private final WorkspaceName name;
    private final WorkspaceUse use;
    private final Instant createdAt;

    private Workspace(WorkspaceId id, WorkspaceName name, WorkspaceUse use, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.name = name;
        this.use = use;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static Workspace rebuild(WorkspaceId id, WorkspaceName name, WorkspaceUse use, Instant createdAt) {
        return new Workspace(id, name, use, createdAt);
    }

    public Workspace named(WorkspaceName name, WorkspaceUse use) {
        return new Workspace(id, Objects.requireNonNull(name), Objects.requireNonNull(use), createdAt);
    }

    public boolean isNamed() {
        return name != null;
    }

    public WorkspaceId id() {
        return id;
    }

    public Optional<WorkspaceName> name() {
        return Optional.ofNullable(name);
    }

    public Optional<WorkspaceUse> use() {
        return Optional.ofNullable(use);
    }

    public Instant createdAt() {
        return createdAt;
    }
}
