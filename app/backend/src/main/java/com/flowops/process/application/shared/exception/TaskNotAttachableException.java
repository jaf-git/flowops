package com.flowops.process.application.shared.exception;

public class TaskNotAttachableException extends RuntimeException {
    public TaskNotAttachableException() {
        super("no such task");
    }
}
