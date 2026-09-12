package com.flowops.workspace.domain.exception;

public class SubjectInactiveException extends RuntimeException {
    public SubjectInactiveException() {
        super("that person is no longer active, so their reporting line cannot be changed");
    }
}
