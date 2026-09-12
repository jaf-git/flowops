package com.flowops.process.application.published;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProcessInstantiationUseCase {
    List<StartableTemplate> startableTemplates();

    UUID startRun(UUID templateId, String name, UUID processOwner);

    StartedRun startRunFromDescriptions(String name, UUID processOwnerId, List<NewStep> steps);

    record StartedRun(UUID instanceId, List<UUID> taskIds) {}

    record NewStep(String title, String description, UUID assignee, Instant deadline, String priority) {}

    record StartableTemplate(UUID id, String name, String overview, int stepCount) {}
}
