package com.flowops.workspace.application.shared.exception;

public class ConfirmationNameMismatchException extends RuntimeException {
    public ConfirmationNameMismatchException() {
        super("the typed name does not match");
    }
}
