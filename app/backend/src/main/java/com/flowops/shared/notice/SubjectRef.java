package com.flowops.shared.notice;

import java.util.UUID;

public record SubjectRef(SubjectKind kind, UUID id) {
    public SubjectRef {
        if (kind == null || id == null) {
            throw new IllegalArgumentException("a notification is always about something identified");
        }
    }

    public static SubjectRef task(UUID id) {
        return new SubjectRef(SubjectKind.TASK, id);
    }

    public static SubjectRef step(UUID id) {
        return new SubjectRef(SubjectKind.STEP, id);
    }

    public static SubjectRef run(UUID id) {
        return new SubjectRef(SubjectKind.RUN, id);
    }

    public static SubjectRef workspace(UUID id) {
        return new SubjectRef(SubjectKind.WORKSPACE, id);
    }

    public static SubjectRef bracket(UUID id) {
        return new SubjectRef(SubjectKind.BRACKET, id);
    }

    public static SubjectRef job(UUID id) {
        return new SubjectRef(SubjectKind.JOB, id);
    }
}
