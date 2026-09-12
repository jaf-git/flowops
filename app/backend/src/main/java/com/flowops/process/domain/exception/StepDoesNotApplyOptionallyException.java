package com.flowops.process.domain.exception;

public class StepDoesNotApplyOptionallyException extends RuntimeException {
    public StepDoesNotApplyOptionallyException() {
        super("this step is not optional, so there is nothing to decide: remove it from the run instead");
    }
}
