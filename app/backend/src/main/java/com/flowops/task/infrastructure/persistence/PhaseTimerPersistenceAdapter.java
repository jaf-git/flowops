package com.flowops.task.infrastructure.persistence;

import com.flowops.task.application.shared.port.PhaseTimerPort;
import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.model.PhaseTimer;
import com.flowops.task.domain.model.PhaseTimerId;
import com.flowops.task.domain.model.TaskId;
import com.flowops.task.infrastructure.persistence.entity.TaskPhaseTimerJpaEntity;
import com.flowops.task.infrastructure.persistence.repository.TaskPhaseTimerJpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PhaseTimerPersistenceAdapter implements PhaseTimerPort {
    private final TaskPhaseTimerJpaRepository phases;

    public PhaseTimerPersistenceAdapter(TaskPhaseTimerJpaRepository phases) {
        this.phases = phases;
    }

    @Override
    public void open(PhaseTimer phase) {
        phases.save(new TaskPhaseTimerJpaEntity(
                phase.id().value(), phase.task().value(), phase.kind().name(), phase.startedAt(), phase.endedAt()));
    }

    @Override
    public void close(PhaseTimer phase) {
        int closed = phases.close(phase.id().value(), phase.endedAt());
        if (closed != 1) {
            throw new IllegalStateException("phase " + phase.id().value() + " was not open");
        }
    }

    @Override
    public Optional<PhaseTimer> openPhaseOf(TaskId task) {
        return phases.findByTaskIdAndEndedAtIsNull(task.value()).map(this::toDomain);
    }

    @Override
    public List<PhaseTimer> openPhasesOf(Collection<TaskId> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        return phases
                .findByTaskIdInAndEndedAtIsNull(
                        tasks.stream().map(TaskId::value).toList())
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<PhaseTimer> allPhasesOf(TaskId task) {
        return phases.findByTaskIdOrderByStartedAtAsc(task.value()).stream()
                .map(this::toDomain)
                .toList();
    }

    private PhaseTimer toDomain(TaskPhaseTimerJpaEntity entity) {
        return new PhaseTimer(
                PhaseTimerId.of(entity.getId()),
                TaskId.of(entity.getTaskId()),
                PhaseKind.valueOf(entity.getPhaseKind()),
                entity.getStartedAt(),
                entity.getEndedAt());
    }
}
