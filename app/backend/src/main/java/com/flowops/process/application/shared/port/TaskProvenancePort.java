package com.flowops.process.application.shared.port;

import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.StepId;
import com.flowops.process.domain.model.TaskRef;

public interface TaskProvenancePort {
    void link(TaskRef task, InstanceId instance, StepId step);

    void unlink(TaskRef task);
}
