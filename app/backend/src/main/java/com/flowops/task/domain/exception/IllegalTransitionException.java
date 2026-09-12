package com.flowops.task.domain.exception;

import com.flowops.task.domain.enums.TaskState;

public class IllegalTransitionException extends RuntimeException {
    private final TaskState from;
    private final TaskState to;

    public IllegalTransitionException(TaskState from, TaskState to) {
        super("a task in " + from + " cannot become " + to);
        this.from = from;
        this.to = to;
    }

    public TaskState from() {
        return from;
    }

    public TaskState to() {
        return to;
    }
}
