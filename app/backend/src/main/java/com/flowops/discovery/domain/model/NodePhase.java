package com.flowops.discovery.domain.model;

import com.flowops.discovery.domain.enums.PhaseKind;
import com.flowops.discovery.domain.enums.WaitingOn;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class NodePhase {
    private final UUID id;
    private final WorkNodeId workNodeId;
    private final PhaseKind kind;
    private final Instant startedAt;
    private final WaitingOn waitingOn;

    private Instant endedAt;

    private NodePhase(
            UUID id, WorkNodeId workNodeId, PhaseKind kind, Instant startedAt, Instant endedAt, WaitingOn waitingOn) {
        this.id = Objects.requireNonNull(id, "a phase row needs an identity");
        this.workNodeId = Objects.requireNonNull(workNodeId, "a phase belongs to exactly one unit of work");
        this.kind = Objects.requireNonNull(kind, "a duration without its phase is the figure I9 forbids");
        this.startedAt = Objects.requireNonNull(startedAt);
        this.endedAt = endedAt;
        this.waitingOn = waitingOn;
    }

    public static NodePhase started(UUID id, WorkNodeId workNodeId, PhaseKind kind, WaitingOn waitingOn, Instant at) {
        Objects.requireNonNull(kind, "a phase row without a kind cannot be read back safely");
        if (kind.isWait() && waitingOn == null) {
            throw new IllegalArgumentException(
                    "a " + kind + " phase needs to say who is being waited on; without it the stretch cannot be "
                            + "kept out of, or let into, a performance figure — invariant I4");
        }
        if (!kind.isWait() && waitingOn != null) {
            throw new IllegalArgumentException("a " + kind + " phase is nobody's silence; it has no waiting-on answer");
        }
        return new NodePhase(id, workNodeId, kind, at, null, waitingOn);
    }

    public static NodePhase rehydrated(
            UUID id, WorkNodeId workNodeId, PhaseKind kind, Instant startedAt, Instant endedAt, WaitingOn waitingOn) {
        return new NodePhase(id, workNodeId, kind, startedAt, endedAt, waitingOn);
    }

    public void seal(Instant at) {
        Objects.requireNonNull(at, "a phase that ended has an end");
        if (endedAt != null) {
            throw new IllegalStateException("phase " + id + " already ended at " + endedAt
                    + "; work that resumes opens a new phase row rather than lengthening this one");
        }
        if (at.isBefore(startedAt)) {
            throw new IllegalArgumentException("a phase cannot end before it began");
        }
        this.endedAt = at;
    }

    public Optional<Duration> elapsed() {
        return endedAt == null ? Optional.empty() : Optional.of(Duration.between(startedAt, endedAt));
    }

    public boolean isOpen() {
        return endedAt == null;
    }

    public boolean isExternalWait() {
        return kind.isExternalWait();
    }

    public UUID id() {
        return id;
    }

    public WorkNodeId workNodeId() {
        return workNodeId;
    }

    public PhaseKind kind() {
        return kind;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Optional<Instant> endedAt() {
        return Optional.ofNullable(endedAt);
    }

    public Optional<WaitingOn> waitingOn() {
        return Optional.ofNullable(waitingOn);
    }
}
