package com.flowops.task.application.shared.exception;

public class NotTheAssigneeException extends RuntimeException {
    public NotTheAssigneeException(String message) {
        super(message);
    }
}
