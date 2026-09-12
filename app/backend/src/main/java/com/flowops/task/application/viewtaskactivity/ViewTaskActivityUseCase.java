package com.flowops.task.application.viewtaskactivity;

import com.flowops.task.domain.model.TaskId;
import java.util.List;

public interface ViewTaskActivityUseCase {
    List<ActivityEntry> execute(TaskId task);
}
