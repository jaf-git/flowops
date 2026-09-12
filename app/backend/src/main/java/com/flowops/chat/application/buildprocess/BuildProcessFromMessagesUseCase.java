package com.flowops.chat.application.buildprocess;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BuildProcessFromMessagesUseCase {
    ProcessDraft draftFrom(UUID conversationId, List<UUID> messageIds);

    UUID startRun(UUID conversationId, RunSubmission submission);

    void appendToTemplate(UUID conversationId, TemplateSubmission submission);

    record RunSubmission(String name, List<SubmittedStep> steps) {}

    record TemplateSubmission(UUID templateId, List<SubmittedStep> steps) {}

    record SubmittedStep(UUID messageId, String title, String description, UUID assigneeId, Instant deadline) {}
}
