package com.flowops.process.domain.exception;

import com.flowops.process.domain.enums.StepCondition;

public class IllegalStepTransitionException extends RuntimeException {
    private final StepCondition from;

    public IllegalStepTransitionException(StepCondition from) {
        super("that step is already " + from.name().toLowerCase());
        this.from = from;
    }

    public StepCondition from() {
        return from;
    }
}
