package com.flowops.task.application.shared;

import com.flowops.task.domain.model.PersonId;
import java.util.Set;

public record TaskAudience(boolean seesEverything, Set<PersonId> assignees, PersonId creator) {
    public static TaskAudience everything() {
        return new TaskAudience(true, Set.of(), null);
    }

    public static TaskAudience of(Set<PersonId> assignees, PersonId creator) {
        return new TaskAudience(false, Set.copyOf(assignees), creator);
    }
}
