package com.flowops.workspace.application.shared.exception;

public class NotAuthenticatedException extends RuntimeException {
    public NotAuthenticatedException() {
        super("there is no caller behind this session");
    }
}
