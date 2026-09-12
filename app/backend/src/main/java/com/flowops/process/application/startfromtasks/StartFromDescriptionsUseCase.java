package com.flowops.process.application.startfromtasks;

import com.flowops.process.domain.model.ProcessInstance;

public interface StartFromDescriptionsUseCase {
    ProcessInstance execute(StartFromDescriptionsCommand command);
}
