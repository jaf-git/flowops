package com.flowops.auth.application.shared.exception;

public class PasscodeRejectedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PasscodeRejectedException() {
        super("the passcode was rejected");
    }
}
