package com.flowops.nodepipeline.domain.notify;

import java.util.UUID;

public record Recipient(UUID person, Standing standing) {
    public Recipient {
        if (person == null || standing == null) {
            throw new IllegalArgumentException("a recipient is a person and the capacity they are addressed in");
        }
    }

    public enum Standing {
        MARKER,

        JOB_OWNER,

        WORKSPACE_OWNER
    }
}
