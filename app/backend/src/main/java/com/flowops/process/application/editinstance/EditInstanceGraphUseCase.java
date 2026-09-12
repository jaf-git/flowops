package com.flowops.process.application.editinstance;

import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.ProcessInstance;
import com.flowops.process.domain.model.StepDependency;
import com.flowops.process.domain.model.StepId;
import java.util.List;

public interface EditInstanceGraphUseCase {
    ProcessInstance remove(InstanceId instance, StepId step);

    ProcessInstance reorder(InstanceId instance, List<StepId> order);

    ProcessInstance draw(InstanceId instance, StepDependency edge);

    ProcessInstance erase(InstanceId instance, StepDependency edge);
}
