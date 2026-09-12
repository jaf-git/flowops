package com.flowops.process.domain.model;

import com.flowops.process.domain.exception.StepNeedsTaskTemplateException;
import java.util.Objects;

public record StepDefinition(
        StepId id,
        TaskTemplateRef taskTemplateId,
        Integer expectedDurationHours,
        int position,
        Applicability applicability) {
    public StepDefinition {
        Objects.requireNonNull(id, "a step identifier is required");

        applicability = applicability == null ? Applicability.always() : applicability;
    }

    public static StepDefinition of(
            StepId id, TaskTemplateRef taskTemplateId, Integer expectedDurationHours, int position) {
        return of(id, taskTemplateId, expectedDurationHours, position, Applicability.always());
    }

    public static StepDefinition of(
            StepId id,
            TaskTemplateRef taskTemplateId,
            Integer expectedDurationHours,
            int position,
            Applicability applicability) {
        if (taskTemplateId == null) {
            throw new StepNeedsTaskTemplateException(position);
        }
        return new StepDefinition(id, taskTemplateId, expectedDurationHours, position, applicability);
    }
}
