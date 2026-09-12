package com.flowops.process.application.shared.port;

import java.util.List;
import java.util.UUID;

public interface TemplateUsesPort {
    List<PlannedIn> plannedIn(UUID taskTemplateId);

    List<CutIn> cutIn(UUID taskTemplateId, int limit);

    int countCutIn(UUID taskTemplateId);

    record PlannedIn(UUID templateId, String name, int position, boolean active) {}

    record CutIn(
            UUID instanceId,
            String instanceName,
            String instanceState,
            UUID stepId,
            String stepCondition,
            UUID taskId,
            java.time.Instant startedAt) {}
}
