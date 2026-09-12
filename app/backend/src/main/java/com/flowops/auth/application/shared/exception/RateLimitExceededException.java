package com.flowops.auth.application.shared.exception;

public class RateLimitExceededException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RateLimitExceededException() {
        super("too many attempts; try again later");
    }
}
