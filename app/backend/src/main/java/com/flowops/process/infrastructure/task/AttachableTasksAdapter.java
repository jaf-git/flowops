package com.flowops.process.infrastructure.task;

import com.flowops.process.application.shared.port.AttachableTasksPort;
import com.flowops.process.domain.model.TaskRef;
import com.flowops.process.infrastructure.persistence.repository.InstanceStepJpaRepository;
import com.flowops.task.application.viewtasks.ViewTasksForCallerUseCase;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AttachableTasksAdapter implements AttachableTasksPort {
    private static final String CLOSED = "CLOSED";

    private final ViewTasksForCallerUseCase viewTasksForCallerUseCase;
    private final InstanceStepJpaRepository steps;

    public AttachableTasksAdapter(
            ViewTasksForCallerUseCase viewTasksForCallerUseCase, InstanceStepJpaRepository steps) {
        this.viewTasksForCallerUseCase = viewTasksForCallerUseCase;
        this.steps = steps;
    }

    @Override
    public List<Attachable> visible() {
        List<ViewTasksForCallerUseCase.Row> mine = viewTasksForCallerUseCase.visibleToCaller();
        Set<UUID> taken = takenAmong(mine);

        List<Attachable> attachable = new ArrayList<>();
        for (ViewTasksForCallerUseCase.Row row : mine) {
            if (!taken.contains(row.id())) {
                attachable.add(asAttachable(row, false));
            }
        }
        return attachable;
    }

    @Override
    public Optional<Attachable> describe(TaskRef task) {
        for (ViewTasksForCallerUseCase.Row row : viewTasksForCallerUseCase.visibleToCaller()) {
            if (row.id().equals(task.value())) {
                return Optional.of(asAttachable(row, steps.existsByTaskId(row.id())));
            }
        }
        return Optional.empty();
    }

    private Set<UUID> takenAmong(List<ViewTasksForCallerUseCase.Row> rows) {
        List<UUID> ids = new ArrayList<>();
        for (ViewTasksForCallerUseCase.Row row : rows) {
            ids.add(row.id());
        }
        return ids.isEmpty() ? Set.of() : new HashSet<>(steps.taskIdsAmong(ids));
    }

    private static Attachable asAttachable(ViewTasksForCallerUseCase.Row row, boolean inAProcess) {
        return new Attachable(
                row.id(),
                row.title(),
                row.state(),
                row.assigneeId(),
                row.deadline(),
                CLOSED.equals(row.state()),
                inAProcess);
    }
}
