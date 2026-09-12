package com.flowops.task.domain.model;

import com.flowops.task.domain.enums.PhaseKind;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record PhaseTimer(PhaseTimerId id, TaskId task, PhaseKind kind, Instant startedAt, Instant endedAt) {
    public PhaseTimer {
        Objects.requireNonNull(id);
        Objects.requireNonNull(task, "a phase belongs to a task");
        Objects.requireNonNull(kind, "a phase has a kind; an unqualified interval is a defect");
        Objects.requireNonNull(startedAt);
        if (endedAt != null && endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("a phase cannot end before it began");
        }
    }

    public static PhaseTimer opened(TaskId task, PhaseKind kind, Instant at) {
        return new PhaseTimer(PhaseTimerId.generate(), task, kind, at, null);
    }

    public PhaseTimer closedAt(Instant at) {
        return new PhaseTimer(id, task, kind, startedAt, at);
    }

    public boolean isOpen() {
        return endedAt == null;
    }

    public Optional<Duration> elapsed() {
        return endedAt == null ? Optional.empty() : Optional.of(Duration.between(startedAt, endedAt));
    }

    public boolean countsAgainstTheAssignee() {
        return kind.countsAgainstTheAssignee();
    }
}
