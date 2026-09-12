package com.flowops.discovery.application.shared.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MyWaitsPort {
    List<WaitOnMe> waitsOnMe(UUID viewer);

    record WaitOnMe(
            UUID waitId,
            String kind,
            UUID onBracketId,
            String onAddress,
            UUID waitingBracketId,
            String waitingAddress,
            String waitingPerformerName,
            UUID conversationId,
            Instant declaredAt) {}
}
