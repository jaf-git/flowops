package com.flowops.chat.application.shared.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BuildProcessPort {
    StartedRun startRunFromDescriptions(String name, UUID processOwnerId, List<NewStep> steps);

    void appendStepsToTemplate(UUID templateId, List<TemplateStep> steps);

    record StartedRun(UUID instanceId, List<UUID> taskIds) {}

    record NewStep(String title, String description, UUID assignee, Instant deadline, String priority) {}

    record TemplateStep(String title, String description) {}
}
