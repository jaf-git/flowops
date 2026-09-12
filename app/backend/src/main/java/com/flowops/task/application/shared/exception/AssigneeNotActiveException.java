package com.flowops.task.application.shared.exception;

public class AssigneeNotActiveException extends RuntimeException {
    public AssigneeNotActiveException(String message) {
        super(message);
    }
}
