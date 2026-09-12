package com.flowops.workspace.domain.exception;

public class SelfManagerException extends RuntimeException {
    public SelfManagerException() {
        super("nobody can report to themselves");
    }
}
