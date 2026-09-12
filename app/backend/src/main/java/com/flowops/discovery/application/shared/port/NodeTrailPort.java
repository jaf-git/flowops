package com.flowops.discovery.application.shared.port;

import com.flowops.discovery.domain.enums.WorkNodeState;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NodeTrailPort {
    List<RecordedMove> trailOf(WorkNodeId node);

    record RecordedMove(
            Optional<WorkNodeState> from,
            WorkNodeState to,
            Optional<UUID> actorId,
            Optional<String> reason,
            Instant occurredAt) {}
}
