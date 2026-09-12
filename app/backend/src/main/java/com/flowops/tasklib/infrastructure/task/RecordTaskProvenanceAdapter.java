package com.flowops.tasklib.infrastructure.task;

import com.flowops.task.application.taskprovenance.SetTaskTemplateProvenanceUseCase;
import com.flowops.tasklib.application.port.RecordTaskProvenancePort;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RecordTaskProvenanceAdapter implements RecordTaskProvenancePort {
    private final SetTaskTemplateProvenanceUseCase setTaskTemplateProvenanceUseCase;

    public RecordTaskProvenanceAdapter(SetTaskTemplateProvenanceUseCase setTaskTemplateProvenanceUseCase) {
        this.setTaskTemplateProvenanceUseCase = setTaskTemplateProvenanceUseCase;
    }

    @Override
    public void stampedFrom(UUID task, UUID template) {
        setTaskTemplateProvenanceUseCase.stampedFrom(task, template);
    }
}
