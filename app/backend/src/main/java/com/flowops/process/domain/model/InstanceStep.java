package com.flowops.process.domain.model;

import com.flowops.process.domain.enums.StepCondition;
import java.time.Instant;
import java.util.Optional;

public record InstanceStep(
        StepId id,
        StepId definitionId,
        TaskTemplateRef taskTemplateId,
        String title,
        String description,
        Integer expectedDurationHours,
        int position,
        Applicability applicability,
        StepCondition condition,
        TaskRef task,
        PersonId assignee,
        Instant reachableAt,
        Instant closedAt,
        Instant skippedAt) {
    public static InstanceStep pending(StepId id, StepDefinition definition, TaskTemplateWork work) {
        return new InstanceStep(
                id,
                definition.id(),
                definition.taskTemplateId(),
                work.title(),
                work.description(),
                definition.expectedDurationHours(),
                definition.position(),
                definition.applicability(),
                StepCondition.PENDING,
                null,
                null,
                null,
                null,
                null);
    }

    public static InstanceStep attached(
            StepId id,
            TaskRef task,
            PersonId assignee,
            String plannedTitle,
            int position,
            boolean closed,
            Instant now) {
        return new InstanceStep(
                id,
                null,
                null,
                plannedTitle,
                null,
                null,
                position,
                Applicability.always(),
                closed ? StepCondition.CLOSED : StepCondition.ASSIGNED,
                task,
                assignee,
                now,
                closed ? now : null,
                null);
    }

    public boolean isAttached() {
        return definitionId == null;
    }

    public InstanceStep at(int newPosition) {
        return new InstanceStep(
                id,
                definitionId,
                taskTemplateId,
                title,
                description,
                expectedDurationHours,
                newPosition,
                applicability,
                condition,
                task,
                assignee,
                reachableAt,
                closedAt,
                skippedAt);
    }

    public InstanceStep backToPending() {
        return new InstanceStep(
                id,
                definitionId,
                taskTemplateId,
                title,
                description,
                expectedDurationHours,
                position,
                applicability,
                StepCondition.PENDING,
                task,
                assignee,
                null,
                closedAt,
                skippedAt);
    }

    public InstanceStep reachableAt(Instant when) {
        if (condition == StepCondition.REACHABLE) {
            return this;
        }
        return new InstanceStep(
                id,
                definitionId,
                taskTemplateId,
                title,
                description,
                expectedDurationHours,
                position,
                applicability,
                StepCondition.REACHABLE,
                task,
                assignee,
                when,
                closedAt,
                skippedAt);
    }

    public InstanceStep assignedTo(TaskRef created, PersonId person) {
        return new InstanceStep(
                id,
                definitionId,
                taskTemplateId,
                title,
                description,
                expectedDurationHours,
                position,
                applicability,
                StepCondition.ASSIGNED,
                created,
                person,
                reachableAt,
                closedAt,
                skippedAt);
    }

    public InstanceStep skippedAt(Instant when) {
        return new InstanceStep(
                id,
                definitionId,
                taskTemplateId,
                title,
                description,
                expectedDurationHours,
                position,
                applicability,
                StepCondition.CLOSED,
                task,
                assignee,
                reachableAt,
                when,
                when);
    }

    public boolean wasSkipped() {
        return skippedAt != null;
    }

    public InstanceStep closedAt(Instant when) {
        return new InstanceStep(
                id,
                definitionId,
                taskTemplateId,
                title,
                description,
                expectedDurationHours,
                position,
                applicability,
                StepCondition.CLOSED,
                task,
                assignee,
                reachableAt,
                when,
                skippedAt);
    }

    public InstanceStep returnedToReachable() {
        return new InstanceStep(
                id,
                definitionId,
                taskTemplateId,
                title,
                description,
                expectedDurationHours,
                position,
                applicability,
                StepCondition.REACHABLE,
                null,
                null,
                reachableAt,
                null,
                skippedAt);
    }

    public Optional<TaskRef> taskReference() {
        return Optional.ofNullable(task);
    }

    public boolean isClosed() {
        return condition == StepCondition.CLOSED;
    }

    public boolean isReachable() {
        return condition == StepCondition.REACHABLE;
    }
}
