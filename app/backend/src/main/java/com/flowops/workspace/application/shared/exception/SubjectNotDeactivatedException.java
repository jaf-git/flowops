package com.flowops.workspace.application.shared.exception;

public class SubjectNotDeactivatedException extends RuntimeException {
    public SubjectNotDeactivatedException() {
        super("a person must be deactivated before they can be erased");
    }
}
