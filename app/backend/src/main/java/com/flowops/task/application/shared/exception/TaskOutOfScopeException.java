package com.flowops.task.application.shared.exception;

public class TaskOutOfScopeException extends RuntimeException {
    public TaskOutOfScopeException(String message) {
        super(message);
    }
}
