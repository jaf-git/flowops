package com.flowops.discovery.application.shared.exception;

public class NotAuthenticatedException extends RuntimeException {
    public NotAuthenticatedException() {
        super("there is no session behind this call");
    }
}
