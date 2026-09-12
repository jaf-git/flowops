package com.flowops.process.application.shared.port;

import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.InstanceStep;
import com.flowops.process.domain.model.ProcessInstance;
import com.flowops.process.domain.model.StepDependency;
import com.flowops.process.domain.model.StepId;
import java.time.Instant;
import java.util.List;

public interface SaveInstancePort {
    void create(ProcessInstance instance);

    void updateAll(ProcessInstance instance);

    void updateStep(InstanceId instance, InstanceStep step);

    void addStep(InstanceId instance, InstanceStep step);

    void removeStep(InstanceId instance, StepId step);

    void replaceEdges(InstanceId instance, List<StepDependency> edges);

    void updatePositions(ProcessInstance instance);

    void abandon(ProcessInstance instance);

    void closeEarly(ProcessInstance instance);

    void archive(InstanceId instance, Instant archivedAt);

    void restore(InstanceId instance);
}
