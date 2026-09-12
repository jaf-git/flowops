package com.flowops.task.infrastructure.persistence;

import com.flowops.task.application.shared.port.LoadTransitionPort;
import com.flowops.task.application.shared.port.RecordTransitionPort;
import com.flowops.task.domain.model.StateTransition;
import com.flowops.task.infrastructure.persistence.entity.TaskStateTransitionJpaEntity;
import com.flowops.task.infrastructure.persistence.repository.TaskStateTransitionJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class TransitionPersistenceAdapter implements RecordTransitionPort, LoadTransitionPort {
    private final TaskStateTransitionJpaRepository transitions;

    public TransitionPersistenceAdapter(TaskStateTransitionJpaRepository transitions) {
        this.transitions = transitions;
    }

    @Override
    public void record(StateTransition transition) {
        transitions.save(new TaskStateTransitionJpaEntity(
                transition.id().value(),
                transition.task().value(),
                transition.cameFrom().map(Enum::name).orElse(null),
                transition.to().name(),
                transition.actor().value(),
                transition.statedReason().orElse(null),
                transition.overridden(),
                transition.occurredAt()));
    }

    @Override
    public java.util.List<StateTransition> allOf(com.flowops.task.domain.model.TaskId task) {
        return transitions.findByTaskIdOrderByOccurredAtAsc(task.value()).stream()
                .map(row -> new StateTransition(
                        com.flowops.task.domain.model.StateTransitionId.of(row.getId()),
                        com.flowops.task.domain.model.TaskId.of(row.getTaskId()),
                        row.getFromState() == null
                                ? null
                                : com.flowops.task.domain.enums.TaskState.valueOf(row.getFromState()),
                        com.flowops.task.domain.enums.TaskState.valueOf(row.getToState()),
                        com.flowops.task.domain.model.PersonId.of(row.getActorUserId()),
                        row.getReason(),
                        row.isOverridden(),
                        row.getOccurredAt()))
                .toList();
    }

    @Override
    public java.util.Optional<String> currentBlockReasonOf(com.flowops.task.domain.model.TaskId task) {
        return transitions
                .findFirstByTaskIdAndToStateOrderByOccurredAtDesc(task.value(), "BLOCKED")
                .map(TaskStateTransitionJpaEntity::getReason);
    }
}
