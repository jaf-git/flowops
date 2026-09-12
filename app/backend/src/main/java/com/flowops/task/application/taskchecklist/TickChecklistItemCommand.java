package com.flowops.task.application.taskchecklist;

import com.flowops.task.domain.model.ChecklistItemId;
import com.flowops.task.domain.model.TaskId;

public record TickChecklistItemCommand(TaskId task, ChecklistItemId item, boolean done) {}
