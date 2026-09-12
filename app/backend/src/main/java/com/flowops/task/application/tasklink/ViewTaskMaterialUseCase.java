package com.flowops.task.application.tasklink;

import com.flowops.task.domain.model.ChecklistItem;
import com.flowops.task.domain.model.TaskId;
import com.flowops.task.domain.model.TaskLink;
import java.util.List;

public interface ViewTaskMaterialUseCase {
    Material execute(TaskId task);

    record Material(List<TaskLink> links, List<ChecklistItem> checklist) {}
}
