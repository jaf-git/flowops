package com.flowops.task.domain.exception;

public class ReassignReasonRequiredException extends RuntimeException {
    public ReassignReasonRequiredException() {
        super("moving somebody's work needs a reason");
    }
}
