package com.flowops.task.domain.exception;

public class ChecklistTextRequiredException extends RuntimeException {
    public ChecklistTextRequiredException() {
        super("a step says what to do");
    }
}
