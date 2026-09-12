package com.flowops.notification.application.shared.exception;

public class NotAuthenticatedException extends RuntimeException {
    public NotAuthenticatedException() {
        super("no session, so there is nobody whose notifications these would be");
    }
}
