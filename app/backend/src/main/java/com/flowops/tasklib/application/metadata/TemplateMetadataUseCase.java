package com.flowops.tasklib.application.metadata;

import com.flowops.tasklib.domain.MetadataField;
import com.flowops.tasklib.domain.TaskTemplate;
import java.util.UUID;

public interface TemplateMetadataUseCase {
    TaskTemplate record(UUID templateId, MetadataField field, String answer);
}
