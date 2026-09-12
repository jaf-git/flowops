package com.flowops.workspace.application.shared.exception;

public class ConsentNotGivenException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ConsentNotGivenException() {
        super("agreement is required before an account can be created");
    }
}
