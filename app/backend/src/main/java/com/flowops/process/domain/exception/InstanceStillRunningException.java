package com.flowops.process.domain.exception;

public class InstanceStillRunningException extends RuntimeException {
    public InstanceStillRunningException() {
        super("a run that is still running cannot be archived");
    }
}
