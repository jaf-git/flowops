package com.flowops.task.domain.exception;

public class OverrideReasonRequiredException extends RuntimeException {
    public OverrideReasonRequiredException() {
        super("forcing a state needs a reason");
    }
}
