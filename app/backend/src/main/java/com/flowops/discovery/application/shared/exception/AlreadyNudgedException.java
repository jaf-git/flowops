package com.flowops.discovery.application.shared.exception;

public class AlreadyNudgedException extends RuntimeException {
    public AlreadyNudgedException(String message) {
        super(message);
    }
}
