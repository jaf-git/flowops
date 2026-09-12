package com.flowops.auth.domain.exception;

public class DisplayNameRequiredException extends RuntimeException {
    public DisplayNameRequiredException() {
        super("a display name is required");
    }
}
