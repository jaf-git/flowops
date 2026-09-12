package com.flowops.tasklib.application.published;

import com.flowops.tasklib.application.port.TaskTemplatePort;
import java.util.List;
import java.util.UUID;

public interface TemplateLifecycleUseCase {
    void retire(UUID templateId);

    void correctEstimate(UUID templateId, long medianWorkMs);

    UUID createApproved(String title, String description, List<String> checklist, UUID author);

    DraftedTemplate createDraft(
            String title,
            String description,
            List<String> checklist,
            String responsibleRole,
            String outputKind,
            UUID author);

    void describeDiscovered(UUID templateId, TaskTemplatePort.DiscoveredFacts facts);

    record DraftedTemplate(UUID id, boolean created) {}
}
