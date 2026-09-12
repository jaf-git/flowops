package com.flowops.task.application.viewtaskstate;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DescribeTaskStateUseCase {
    List<TaskSnapshot> describe(Collection<UUID> tasks);

    record TaskSnapshot(
            UUID task,
            String title,
            String description,
            String priority,
            String state,
            String blockedReason,
            UUID assignee,
            Instant deadline,
            boolean atRisk,
            List<Phase> phases) {}

    record Phase(String kind, long seconds) {}
}
