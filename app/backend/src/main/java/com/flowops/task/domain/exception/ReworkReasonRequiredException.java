package com.flowops.task.domain.exception;

public class ReworkReasonRequiredException extends RuntimeException {
    public ReworkReasonRequiredException() {
        super("sending work back is saying what is wrong with it");
    }
}
