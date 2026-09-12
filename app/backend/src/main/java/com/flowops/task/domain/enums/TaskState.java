package com.flowops.task.domain.enums;

import com.flowops.task.domain.exception.IllegalTransitionException;
import java.util.Map;
import java.util.Set;

public enum TaskState {
    CREATED,
    ACCEPTED,
    IN_PROGRESS,
    BLOCKED,
    COMPLETED,
    APPROVED,
    CLOSED;

    private static final Map<TaskState, Set<TaskState>> LEGAL_MOVES = Map.of(
            CREATED, Set.of(CREATED, ACCEPTED),
            ACCEPTED, Set.of(IN_PROGRESS),
            IN_PROGRESS, Set.of(BLOCKED, COMPLETED),
            BLOCKED, Set.of(IN_PROGRESS),
            COMPLETED, Set.of(IN_PROGRESS, APPROVED),
            APPROVED, Set.of(CLOSED),
            CLOSED, Set.of());

    public boolean mayBecome(TaskState next) {
        return LEGAL_MOVES.getOrDefault(this, Set.of()).contains(next);
    }

    public TaskState moveTo(TaskState next) {
        if (!mayBecome(next)) {
            throw new IllegalTransitionException(this, next);
        }
        return next;
    }

    public java.util.Optional<PhaseKind> openPhase() {
        return switch (this) {
            case CREATED, ACCEPTED -> java.util.Optional.of(PhaseKind.WAIT);
            case IN_PROGRESS -> java.util.Optional.of(PhaseKind.ACTIVE);
            case BLOCKED -> java.util.Optional.of(PhaseKind.BLOCKED);
            case COMPLETED -> java.util.Optional.of(PhaseKind.REVIEW);
            case APPROVED -> java.util.Optional.of(PhaseKind.APPROVAL);
            case CLOSED -> java.util.Optional.empty();
        };
    }
}
