package com.flowops.auth.application.shared.exception;

public class ReauthenticationRequiredException extends RuntimeException {
    public ReauthenticationRequiredException() {
        super("This action requires re-authentication.");
    }
}
