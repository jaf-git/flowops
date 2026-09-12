package com.flowops.task.application.taskprovenance;

import java.util.UUID;

public interface SetTaskTemplateProvenanceUseCase {
    void stampedFrom(UUID task, UUID template);
}
