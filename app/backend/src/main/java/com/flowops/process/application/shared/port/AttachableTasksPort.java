package com.flowops.process.application.shared.port;

import com.flowops.process.domain.model.TaskRef;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttachableTasksPort {
    List<Attachable> visible();

    Optional<Attachable> describe(TaskRef task);

    record Attachable(
            UUID id,
            String title,
            String state,
            UUID assigneeId,
            Instant deadline,
            boolean closed,
            boolean inAProcess) {}
}
