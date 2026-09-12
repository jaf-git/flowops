package com.flowops.process.application.startfromtasks;

import com.flowops.process.domain.model.ProcessInstance;

public interface StartFromTasksUseCase {
    ProcessInstance execute(StartFromTasksCommand command);
}
