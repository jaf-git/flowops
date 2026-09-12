package com.flowops.process.domain.exception;

import com.flowops.process.domain.model.StepId;
import java.util.List;

public class CycleWouldFormException extends RuntimeException {
    private final List<StepId> cycle;

    public CycleWouldFormException(List<StepId> cycle) {
        super("that would make " + cycle.size() + " steps wait for each other");
        this.cycle = List.copyOf(cycle);
    }

    public List<StepId> cycle() {
        return cycle;
    }
}
