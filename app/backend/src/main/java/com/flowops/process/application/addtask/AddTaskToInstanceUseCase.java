package com.flowops.process.application.addtask;

import com.flowops.process.domain.model.ProcessInstance;
import java.util.UUID;

public interface AddTaskToInstanceUseCase {
    Added execute(AddTaskToInstanceCommand command);

    record Added(ProcessInstance instance, UUID stepId, UUID taskId) {}
}
