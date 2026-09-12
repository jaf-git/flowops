package com.flowops.workspace.domain.exception;

public class InvalidEmailAddressException extends RuntimeException {
    public InvalidEmailAddressException(String message) {
        super(message);
    }
}
