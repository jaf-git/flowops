package com.flowops.discovery.domain.model;

import com.flowops.discovery.domain.enums.WorkNodeState;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record NodeStateTransition(WorkNodeState from, WorkNodeState to, UUID actorId, String reason) {
    public NodeStateTransition {
        Objects.requireNonNull(to, "a move that arrives nowhere is not a move");
    }

    public Optional<WorkNodeState> cameFrom() {
        return Optional.ofNullable(from);
    }

    public Optional<UUID> actor() {
        return Optional.ofNullable(actorId);
    }

    public Optional<String> statedReason() {
        return Optional.ofNullable(reason);
    }
}
