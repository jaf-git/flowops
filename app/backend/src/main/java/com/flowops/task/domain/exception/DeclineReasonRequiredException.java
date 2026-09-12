package com.flowops.task.domain.exception;

public class DeclineReasonRequiredException extends RuntimeException {
    public DeclineReasonRequiredException() {
        super("A refused proposal with no answer in it");
    }
}
