package com.flowops.chat.application.assignwork;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignWorkInConversationUseCase {
    AssignmentContext contextFor(UUID conversationId);

    UUID assignTask(UUID conversationId, TaskDraft draft);

    UUID startRun(UUID conversationId, UUID templateId);

    record AssignmentContext(
            Optional<Counterpart> counterpart,
            boolean mayAssignTask,
            boolean mayStartRun,
            List<StartableTemplate> templates) {}

    record Counterpart(UUID id, String displayName, boolean active) {}

    record StartableTemplate(UUID id, String name, String overview, int stepCount) {}

    record TaskDraft(String title, String description, Instant deadline, String priority) {}
}
