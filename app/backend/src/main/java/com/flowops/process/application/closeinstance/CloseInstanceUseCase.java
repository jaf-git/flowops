package com.flowops.process.application.closeinstance;

import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.ProcessInstance;

public interface CloseInstanceUseCase {
    ProcessInstance execute(InstanceId instance, String note);
}
