package com.flowops.discovery.application.markmessage;

import com.flowops.discovery.domain.enums.Direction;
import com.flowops.discovery.domain.model.TrackId;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.util.Optional;
import java.util.UUID;

public interface MarkMessageUseCase {
    Marked execute(MarkMessage command);

    record MarkMessage(UUID messageId, UUID jobId, Direction direction, UUID performerId) {}

    record Marked(WorkNodeId node, Optional<TrackId> track, UUID conversationId) {}
}
