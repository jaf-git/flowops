package com.flowops.task.application.shared.exception;

public class NotTheCreatorException extends RuntimeException {
    public NotTheCreatorException(String message) {
        super(message);
    }
}
