package com.flowops.tasklib.application.exception;

public class NotTheAuthorException extends RuntimeException {
    public NotTheAuthorException(String message) {
        super(message);
    }
}
