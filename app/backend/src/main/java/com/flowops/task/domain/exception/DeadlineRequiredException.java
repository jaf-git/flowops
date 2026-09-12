package com.flowops.task.domain.exception;

public class DeadlineRequiredException extends RuntimeException {
    public DeadlineRequiredException() {
        super("a task has a deadline; without one it cannot be measured");
    }
}
