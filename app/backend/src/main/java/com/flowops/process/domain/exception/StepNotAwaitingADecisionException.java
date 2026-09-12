package com.flowops.process.domain.exception;

import com.flowops.process.domain.model.StepId;

public class StepNotAwaitingADecisionException extends RuntimeException {
    private final StepId step;

    public StepNotAwaitingADecisionException(StepId step) {
        super("this step is not waiting on a decision: the question is asked once, when it becomes reachable");
        this.step = step;
    }

    public StepId step() {
        return step;
    }
}
