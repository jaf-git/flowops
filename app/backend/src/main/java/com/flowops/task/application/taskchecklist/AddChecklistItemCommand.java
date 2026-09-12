package com.flowops.task.application.taskchecklist;

import com.flowops.task.domain.model.TaskId;

public record AddChecklistItemCommand(TaskId task, String text) {}
