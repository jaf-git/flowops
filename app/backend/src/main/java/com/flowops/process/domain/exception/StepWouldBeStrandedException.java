package com.flowops.process.domain.exception;

import com.flowops.process.domain.model.StepId;
import java.util.Set;

public class StepWouldBeStrandedException extends RuntimeException {
    private final Set<StepId> stranded;

    public StepWouldBeStrandedException(Set<StepId> stranded) {
        super("that would leave " + stranded.size() + " steps that nothing ever reaches");
        this.stranded = Set.copyOf(stranded);
    }

    public Set<StepId> stranded() {
        return stranded;
    }
}
