package com.flowops.auth.application.shared.exception;

public class PasscodeAttemptLockedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PasscodeAttemptLockedException() {
        super("the signup attempt is locked and a new passcode must be requested");
    }
}
