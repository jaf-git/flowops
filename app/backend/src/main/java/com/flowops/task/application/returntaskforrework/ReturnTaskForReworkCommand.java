package com.flowops.task.application.returntaskforrework;

import com.flowops.task.domain.model.TaskId;

public record ReturnTaskForReworkCommand(TaskId task, String reason) {}
