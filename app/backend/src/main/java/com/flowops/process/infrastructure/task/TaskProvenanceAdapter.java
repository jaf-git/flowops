package com.flowops.process.infrastructure.task;

import com.flowops.process.application.shared.port.TaskProvenancePort;
import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.StepId;
import com.flowops.process.domain.model.TaskRef;
import com.flowops.task.application.taskprovenance.SetTaskProcessProvenanceUseCase;
import org.springframework.stereotype.Component;

@Component
public class TaskProvenanceAdapter implements TaskProvenancePort {
    private final SetTaskProcessProvenanceUseCase setTaskProcessProvenanceUseCase;

    public TaskProvenanceAdapter(SetTaskProcessProvenanceUseCase setTaskProcessProvenanceUseCase) {
        this.setTaskProcessProvenanceUseCase = setTaskProcessProvenanceUseCase;
    }

    @Override
    public void link(TaskRef task, InstanceId instance, StepId step) {
        setTaskProcessProvenanceUseCase.link(task.value(), instance.value(), step.value());
    }

    @Override
    public void unlink(TaskRef task) {
        setTaskProcessProvenanceUseCase.unlink(task.value());
    }
}
