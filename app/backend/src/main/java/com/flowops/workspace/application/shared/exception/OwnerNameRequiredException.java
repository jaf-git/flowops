package com.flowops.workspace.application.shared.exception;

public class OwnerNameRequiredException extends RuntimeException {
    public OwnerNameRequiredException(Throwable cause) {
        super("a name for the owner is required", cause);
    }
}
