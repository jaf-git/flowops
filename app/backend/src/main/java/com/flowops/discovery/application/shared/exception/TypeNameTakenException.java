package com.flowops.discovery.application.shared.exception;

public class TypeNameTakenException extends RuntimeException {
    public TypeNameTakenException(String message) {
        super(message);
    }
}
