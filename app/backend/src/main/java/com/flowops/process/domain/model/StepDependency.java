package com.flowops.process.domain.model;

import com.flowops.process.domain.exception.CycleWouldFormException;
import java.util.List;
import java.util.Objects;

public record StepDependency(StepId dependent, StepId dependsOn) {
    public StepDependency {
        Objects.requireNonNull(dependent, "an edge has a dependent step");
        Objects.requireNonNull(dependsOn, "an edge has a step it depends on");
    }

    public static StepDependency of(StepId dependent, StepId dependsOn) {
        if (dependent.equals(dependsOn)) {
            throw new CycleWouldFormException(List.of(dependent));
        }
        return new StepDependency(dependent, dependsOn);
    }
}
