package com.flowops.task.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record Approval(
        ApprovalId id, TaskId task, ApprovalScore score, String comment, PersonId reviewer, Instant decidedAt) {
    public Approval {
        Objects.requireNonNull(id);
        Objects.requireNonNull(task, "a judgement is about a task");
        Objects.requireNonNull(score, "approving is saying how the work was");
        Objects.requireNonNull(reviewer, "a judgement was made by somebody");
        Objects.requireNonNull(decidedAt);

        comment = comment == null || comment.isBlank() ? null : comment.trim();
    }

    public static Approval of(TaskId task, ApprovalScore score, String comment, PersonId reviewer, Instant at) {
        return new Approval(ApprovalId.generate(), task, score, comment, reviewer, at);
    }

    public Optional<String> writtenComment() {
        return Optional.ofNullable(comment);
    }
}
