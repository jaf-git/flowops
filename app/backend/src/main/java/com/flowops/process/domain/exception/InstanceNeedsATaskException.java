package com.flowops.process.domain.exception;

public class InstanceNeedsATaskException extends RuntimeException {
    public InstanceNeedsATaskException() {
        super("a run with no work in it is not a run");
    }
}
