package com.flowops.discovery.domain.model;

import com.flowops.discovery.domain.enums.CloseKind;
import com.flowops.discovery.domain.enums.WaitKind;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class WorkNodeWait {
    private final UUID id;
    private final BracketId bracketId;
    private final WaitKind kind;
    private final BracketId onBracketId;
    private final String reason;
    private final Instant expectedBy;
    private final Instant openedAt;

    private Instant satisfiedAt;
    private Instant cancelledAt;

    private WorkNodeWait(
            UUID id,
            BracketId bracketId,
            WaitKind kind,
            BracketId onBracketId,
            String reason,
            Instant expectedBy,
            Instant openedAt) {
        this.id = Objects.requireNonNull(id);
        this.bracketId = Objects.requireNonNull(bracketId, "a wait belongs to the bracket that is blocked");
        this.kind = Objects.requireNonNull(kind, "R7.1 - a wait names what it is waiting on");
        this.onBracketId = onBracketId;
        this.reason = reason;
        this.expectedBy = expectedBy;
        this.openedAt = Objects.requireNonNull(openedAt);
    }

    public static WorkNodeWait declared(
            UUID id, WorkBracket waiter, WaitKind kind, WorkBracket on, String reason, Instant expectedBy, Instant at) {
        Objects.requireNonNull(waiter, "somebody is waiting");

        if (on != null) {
            if (!on.jobId().equals(waiter.jobId())) {
                throw new IllegalArgumentException(
                        "R7.10 - a wait may not cross a job; the outer wall must not leak through the dependency mechanism");
            }
            if (on.id().equals(waiter.id())) {
                throw new IllegalArgumentException("a bracket does not wait on itself");
            }
        }

        Instant expectation = expectedBy != null ? expectedBy : at.plus(kind.defaultExpectation());

        WorkNodeWait wait = new WorkNodeWait(
                id, waiter.id(), kind, on == null ? null : on.id(), blankToNull(reason), expectation, at);

        if (on != null && on.closeKind().map(CloseKind::satisfiesAWait).orElse(false)) {
            wait.satisfiedAt = at;
        }

        return wait;
    }

    public static WorkNodeWait rehydrated(
            UUID id,
            BracketId bracketId,
            WaitKind kind,
            BracketId onBracketId,
            String reason,
            Instant expectedBy,
            Instant openedAt,
            Instant satisfiedAt,
            Instant cancelledAt) {
        WorkNodeWait wait = new WorkNodeWait(id, bracketId, kind, onBracketId, reason, expectedBy, openedAt);
        wait.satisfiedAt = satisfiedAt;
        wait.cancelledAt = cancelledAt;
        return wait;
    }

    public void satisfiedBy(CloseKind closeKind, Instant at) {
        Objects.requireNonNull(closeKind, "something closed this wait; say what");
        if (!closeKind.satisfiesAWait()) {
            throw new IllegalArgumentException(
                    "R7.6 - " + closeKind + " is not a completion and does not satisfy a wait; "
                            + "the waiter is told the thing they awaited died (N8) instead");
        }
        refuseIfEnded();
        this.satisfiedAt = Objects.requireNonNull(at);
    }

    public void cancelledBecauseItDied(Instant at) {
        refuseIfEnded();
        this.cancelledAt = Objects.requireNonNull(at);
    }

    public void withdrawn(Instant at) {
        refuseIfEnded();
        this.cancelledAt = Objects.requireNonNull(at);
    }

    public WorkNodeWait reTargetedTo(UUID newId, WorkBracket successor) {
        Objects.requireNonNull(successor, "a re-target names the bracket that took the work on");
        if (isEnded()) {
            throw new IllegalStateException("a wait that already ended does not re-target");
        }
        return new WorkNodeWait(newId, bracketId, kind, successor.id(), reason, expectedBy, openedAt);
    }

    public boolean isOpen() {
        return satisfiedAt == null && cancelledAt == null;
    }

    private boolean isEnded() {
        return !isOpen();
    }

    public boolean isExternal() {
        return kind.isExternal();
    }

    public boolean isOverdueAt(Instant now) {
        return isOpen() && expectedBy != null && now.isAfter(expectedBy);
    }

    private void refuseIfEnded() {
        if (isEnded()) {
            throw new IllegalStateException("wait " + id + " has already ended");
        }
    }

    private static String blankToNull(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    public UUID id() {
        return id;
    }

    public BracketId bracketId() {
        return bracketId;
    }

    public WaitKind kind() {
        return kind;
    }

    public Optional<BracketId> onBracketId() {
        return Optional.ofNullable(onBracketId);
    }

    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    public Optional<Instant> expectedBy() {
        return Optional.ofNullable(expectedBy);
    }

    public Instant openedAt() {
        return openedAt;
    }

    public Optional<Instant> satisfiedAt() {
        return Optional.ofNullable(satisfiedAt);
    }

    public Optional<Instant> cancelledAt() {
        return Optional.ofNullable(cancelledAt);
    }
}
