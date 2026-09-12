package com.flowops.auth.domain.exception;

public class InvalidEmailAddressException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidEmailAddressException() {
        super("the email address is not a valid address");
    }
}
