package com.flowops.workspace.application.shared.exception;

public class OnlyOwnerException extends RuntimeException {
    public OnlyOwnerException() {
        super("The last active owner cannot be deactivated.");
    }
}
