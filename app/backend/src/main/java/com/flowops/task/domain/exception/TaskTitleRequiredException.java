package com.flowops.task.domain.exception;

import com.flowops.shared.domain.RefusedByDomain;

public class TaskTitleRequiredException extends RefusedByDomain {
    public TaskTitleRequiredException() {
        super("TASK_TITLE_REQUIRED", "a task has a title; it is what appears in every list");
    }
}
