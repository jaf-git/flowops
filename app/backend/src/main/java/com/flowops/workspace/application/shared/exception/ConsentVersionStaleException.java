package com.flowops.workspace.application.shared.exception;

public class ConsentVersionStaleException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ConsentVersionStaleException() {
        super("the terms changed while you were reading them; please read them again");
    }
}
