package com.flowops.notification.application.shared.exception;

public class NotYoursException extends RuntimeException {
    public NotYoursException() {
        super("no such notification");
    }
}
