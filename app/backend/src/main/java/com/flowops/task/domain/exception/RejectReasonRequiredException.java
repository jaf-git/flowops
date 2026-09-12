package com.flowops.task.domain.exception;

public class RejectReasonRequiredException extends RuntimeException {
    public RejectReasonRequiredException() {
        super("A rejection with no reason");
    }
}
