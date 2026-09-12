package com.flowops.process.api.dto;

import com.flowops.process.application.viewtemplates.ViewTemplateUsesUseCase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TemplateUsesResponse(List<PlannedInResponse> processTemplates, List<RunResponse> runs, int runsTotal) {
    public record PlannedInResponse(UUID templateId, String name, int position, boolean active) {}

    public record RunResponse(
            UUID instanceId,
            String instanceName,
            String instanceState,
            UUID stepId,
            String stepCondition,
            UUID taskId,
            Instant startedAt) {}

    public static TemplateUsesResponse of(ViewTemplateUsesUseCase.Uses uses) {
        return new TemplateUsesResponse(
                uses.processTemplates().stream()
                        .map(each ->
                                new PlannedInResponse(each.templateId(), each.name(), each.position(), each.active()))
                        .toList(),
                uses.runs().stream()
                        .map(each -> new RunResponse(
                                each.instanceId(),
                                each.instanceName(),
                                each.instanceState(),
                                each.stepId(),
                                each.stepCondition(),
                                each.taskId(),
                                each.startedAt()))
                        .toList(),
                uses.runsTotal());
    }
}
