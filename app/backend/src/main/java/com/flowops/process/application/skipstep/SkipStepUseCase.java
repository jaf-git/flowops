package com.flowops.process.application.skipstep;

import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.StepId;

public interface SkipStepUseCase {
    void skip(InstanceId instance, StepId step);
}
