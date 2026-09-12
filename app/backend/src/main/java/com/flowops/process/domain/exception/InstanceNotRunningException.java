package com.flowops.process.domain.exception;

public class InstanceNotRunningException extends RuntimeException {
    public InstanceNotRunningException() {
        super("a finished run does not reopen");
    }
}
