package com.flowops.auth.application.shared.exception;

public class AuthenticationRefusedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AuthenticationRefusedException() {
        super("authentication was refused");
    }
}
