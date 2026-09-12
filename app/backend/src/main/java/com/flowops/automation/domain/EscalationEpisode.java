package com.flowops.automation.domain;

import java.time.Instant;
import java.util.UUID;

public record EscalationEpisode(UUID id, UUID taskId, Rung rung, Instant lastFiredAt) {
    public EscalationEpisode {
        if (id == null || taskId == null || rung == null || lastFiredAt == null) {
            throw new IllegalArgumentException("an episode is a task, a rung and the instant it last fired");
        }
    }
}
