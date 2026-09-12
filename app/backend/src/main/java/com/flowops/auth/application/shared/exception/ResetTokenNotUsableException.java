package com.flowops.auth.application.shared.exception;

public class ResetTokenNotUsableException extends RuntimeException {
    public ResetTokenNotUsableException() {
        super("The reset link cannot be used.");
    }
}
