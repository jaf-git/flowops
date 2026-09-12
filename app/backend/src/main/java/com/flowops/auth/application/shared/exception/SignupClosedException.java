package com.flowops.auth.application.shared.exception;

public class SignupClosedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SignupClosedException() {
        super("this installation already has an owner, so signing up is closed");
    }
}
