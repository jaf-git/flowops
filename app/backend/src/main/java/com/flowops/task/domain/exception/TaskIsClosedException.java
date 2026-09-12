package com.flowops.task.domain.exception;

public class TaskIsClosedException extends RuntimeException {
    public TaskIsClosedException() {
        super("An edit to work that is finished");
    }
}
