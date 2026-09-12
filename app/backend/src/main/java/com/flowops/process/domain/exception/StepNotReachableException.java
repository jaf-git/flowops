package com.flowops.process.domain.exception;

import com.flowops.process.domain.model.StepId;
import java.util.Set;

public class StepNotReachableException extends RuntimeException {
    private final Set<StepId> unmet;

    public StepNotReachableException(Set<StepId> unmet) {
        super("that step is waiting on " + unmet.size() + " others");
        this.unmet = Set.copyOf(unmet);
    }

    public Set<StepId> unmet() {
        return unmet;
    }
}
