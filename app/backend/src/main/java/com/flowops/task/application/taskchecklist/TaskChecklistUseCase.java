package com.flowops.task.application.taskchecklist;

import com.flowops.task.domain.model.ChecklistItem;

public interface TaskChecklistUseCase {
    ChecklistItem add(AddChecklistItemCommand command);

    ChecklistItem tick(TickChecklistItemCommand command);

    void remove(RemoveChecklistItemCommand command);
}
