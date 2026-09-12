package com.flowops.process.application.shared.port;

import com.flowops.process.domain.model.TaskRef;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface TaskStatePort {
    Map<TaskRef, TaskProgress> describe(Collection<TaskRef> tasks);

    record TaskProgress(
            String title,
            String description,
            String priority,
            String state,
            String blockedReason,
            UUID assignee,
            Instant deadline,
            boolean atRisk,
            List<Phase> phases,
            Duration waiting) {}

    record Phase(String kind, long seconds) {}
}
