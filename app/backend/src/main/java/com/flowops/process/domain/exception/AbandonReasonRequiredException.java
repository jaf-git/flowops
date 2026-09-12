package com.flowops.process.domain.exception;

public class AbandonReasonRequiredException extends RuntimeException {
    public AbandonReasonRequiredException() {
        super("stopping a run needs a reason");
    }
}
