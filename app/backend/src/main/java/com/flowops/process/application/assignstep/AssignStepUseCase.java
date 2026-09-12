package com.flowops.process.application.assignstep;

import com.flowops.process.domain.model.ProcessInstance;

public interface AssignStepUseCase {
    ProcessInstance execute(AssignStepCommand command);
}
