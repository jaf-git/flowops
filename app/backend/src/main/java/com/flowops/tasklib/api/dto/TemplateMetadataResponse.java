package com.flowops.tasklib.api.dto;

import com.flowops.tasklib.domain.MetadataField;
import com.flowops.tasklib.domain.TemplateMetadata;
import java.util.List;

public record TemplateMetadataResponse(
        String responsibleRole,
        String triggerNote,
        String requiredInput,
        String expectedOutput,
        String outputKind,
        String completionCriteria,
        String nextAsk,
        List<String> missing) {
    public static TemplateMetadataResponse of(TemplateMetadata metadata) {
        return new TemplateMetadataResponse(
                metadata.responsibleRole(),
                metadata.triggerNote(),
                metadata.requiredInput(),
                metadata.expectedOutput(),
                metadata.outputKind() == null ? null : metadata.outputKind().name(),
                metadata.completionCriteria(),
                metadata.nextMissing().map(Enum::name).orElse(null),
                metadata.missing().stream().map(MetadataField::name).toList());
    }
}
