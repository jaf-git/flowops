package com.flowops.task.application.shared.port;

import com.flowops.task.domain.model.ChecklistItem;
import com.flowops.task.domain.model.ChecklistItemId;
import com.flowops.task.domain.model.TaskId;
import com.flowops.task.domain.model.TaskLink;
import com.flowops.task.domain.model.TaskLinkId;
import java.util.List;
import java.util.Optional;

public interface TaskMaterialPort {
    List<TaskLink> linksOf(TaskId task);

    void attach(TaskLink link);

    void detach(TaskId task, TaskLinkId link);

    Optional<TaskLink> findLink(TaskId task, TaskLinkId link);

    List<ChecklistItem> checklistOf(TaskId task);

    void add(ChecklistItem item);

    void update(ChecklistItem item);

    void remove(TaskId task, ChecklistItemId item);

    Optional<ChecklistItem> findItem(TaskId task, ChecklistItemId item);

    int nextPosition(TaskId task);
}
