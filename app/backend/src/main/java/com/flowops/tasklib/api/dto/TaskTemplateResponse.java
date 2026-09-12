package com.flowops.tasklib.api.dto;

import com.flowops.tasklib.domain.TaskTemplate;
import com.flowops.tasklib.domain.shape.ProcessShapeHint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TaskTemplateResponse(
        UUID id,
        String title,
        String description,
        String type,
        String priority,
        BigDecimal estimatedHours,
        List<String> checklist,
        String status,
        UUID authorId,
        int timesUsed,
        String rejectionReason,
        UUID convertedToProcessTemplateId,
        List<ProcessShapeHint> processShapeHints,
        TemplateMetadataResponse metadata,
        Instant createdAt,
        Instant updatedAt,
        boolean discoveredByPipeline) {
    public static TaskTemplateResponse of(TaskTemplate template, List<ProcessShapeHint> hints) {
        return new TaskTemplateResponse(
                template.id(),
                template.details().title(),
                template.details().description(),
                template.details().type(),
                template.details().priority(),
                template.details().estimatedHours(),
                template.details().checklist(),
                template.status().name(),
                template.authorId(),
                template.timesUsed(),
                template.rejectionReason(),
                template.convertedToProcessTemplateId(),
                hints,
                TemplateMetadataResponse.of(template.metadata()),
                template.createdAt(),
                template.updatedAt(),
                template.discoveredByPipeline());
    }
}
