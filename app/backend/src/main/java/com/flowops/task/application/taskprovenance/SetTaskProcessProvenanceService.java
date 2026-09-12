package com.flowops.task.application.taskprovenance;

import com.flowops.task.application.shared.port.SaveTaskPort;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SetTaskProcessProvenanceService implements SetTaskProcessProvenanceUseCase {
    private final SaveTaskPort saveTaskPort;

    public SetTaskProcessProvenanceService(SaveTaskPort saveTaskPort) {
        this.saveTaskPort = saveTaskPort;
    }

    @Override
    @Transactional
    public void link(UUID task, UUID instance, UUID step) {
        saveTaskPort.setProcessProvenance(task, instance, step);
    }

    @Override
    @Transactional
    public void unlink(UUID task) {
        saveTaskPort.setProcessProvenance(task, null, null);
    }
}
