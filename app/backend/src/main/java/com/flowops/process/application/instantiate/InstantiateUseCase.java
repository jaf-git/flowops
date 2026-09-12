package com.flowops.process.application.instantiate;

import com.flowops.process.domain.model.ProcessInstance;

public interface InstantiateUseCase {
    ProcessInstance execute(InstantiateCommand command);
}
