package com.flowops.process.domain.exception;

public class ConditionNeedsAnOptionalStepException extends RuntimeException {
    public ConditionNeedsAnOptionalStepException() {
        super("a condition describes when an optional step applies, so the step must be optional");
    }
}
