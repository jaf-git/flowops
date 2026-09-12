package com.flowops.discovery.application.shared.exception;

public class OutputAlreadyRecordedException extends RuntimeException {
    public OutputAlreadyRecordedException(String message) {
        super(message);
    }
}
