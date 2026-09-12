package com.flowops.discovery.application.assignorphan;

import com.flowops.discovery.domain.enums.Direction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AssignOrphanUseCase {
    List<Orphan> awaitingPlacement();

    void place(UUID nodeId, UUID trackId);

    record Orphan(
            UUID nodeId, UUID jobId, String jobName, String text, Direction direction, Instant markedAt, UUID saidBy) {}
}
