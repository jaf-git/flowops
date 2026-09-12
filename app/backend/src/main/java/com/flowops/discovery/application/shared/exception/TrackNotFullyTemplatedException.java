package com.flowops.discovery.application.shared.exception;

public class TrackNotFullyTemplatedException extends RuntimeException {
    public TrackNotFullyTemplatedException(String message) {
        super(message);
    }
}
