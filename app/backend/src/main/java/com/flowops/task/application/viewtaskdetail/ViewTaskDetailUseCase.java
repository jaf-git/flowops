package com.flowops.task.application.viewtaskdetail;

import com.flowops.task.domain.model.TaskId;

public interface ViewTaskDetailUseCase {
    ViewTaskDetailResult execute(TaskId task);
}
