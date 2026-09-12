package com.flowops.process.application.abandoninstance;

import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.ProcessInstance;

public interface AbandonInstanceUseCase {
    ProcessInstance execute(InstanceId instance, String reason);
}
