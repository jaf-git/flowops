package com.flowops.task.application.taskprovenance;

import com.flowops.task.application.shared.port.SaveTaskPort;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SetTaskTemplateProvenanceService implements SetTaskTemplateProvenanceUseCase {
    private final SaveTaskPort saveTaskPort;

    public SetTaskTemplateProvenanceService(SaveTaskPort saveTaskPort) {
        this.saveTaskPort = saveTaskPort;
    }

    @Override
    @Transactional
    public void stampedFrom(UUID task, UUID template) {
        saveTaskPort.setTemplateProvenance(task, template);
    }
}
