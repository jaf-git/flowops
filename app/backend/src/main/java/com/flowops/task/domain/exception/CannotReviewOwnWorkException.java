package com.flowops.task.domain.exception;

public class CannotReviewOwnWorkException extends RuntimeException {
    public CannotReviewOwnWorkException() {
        super("nobody judges their own work, whatever authority they hold");
    }
}
