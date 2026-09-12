package com.flowops.discovery.application.shared.exception;

public class TrackAlreadyClosedException extends RuntimeException {
    public TrackAlreadyClosedException(String message) {
        super(message);
    }
}
