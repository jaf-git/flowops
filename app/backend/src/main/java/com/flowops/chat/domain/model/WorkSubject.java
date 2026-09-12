package com.flowops.chat.domain.model;

import java.util.Objects;
import java.util.UUID;

public record WorkSubject(WorkSubjectKind kind, UUID id) {
    public WorkSubject {
        Objects.requireNonNull(kind, "a kind of work is required");
        Objects.requireNonNull(id, "an identifier is required");
    }

    public static WorkSubject task(UUID task) {
        return new WorkSubject(WorkSubjectKind.TASK, task);
    }

    public static WorkSubject run(UUID instance) {
        return new WorkSubject(WorkSubjectKind.RUN, instance);
    }
}
