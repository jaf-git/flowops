package com.flowops.discovery.application.shared.exception;

public class UndoWindowClosedException extends RuntimeException {
    public UndoWindowClosedException(String message) {
        super(message);
    }
}
