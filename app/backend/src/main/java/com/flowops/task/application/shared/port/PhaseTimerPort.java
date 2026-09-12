package com.flowops.task.application.shared.port;

import com.flowops.task.domain.model.PhaseTimer;
import com.flowops.task.domain.model.TaskId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PhaseTimerPort {
    void open(PhaseTimer phase);

    void close(PhaseTimer phase);

    Optional<PhaseTimer> openPhaseOf(TaskId task);

    List<PhaseTimer> openPhasesOf(Collection<TaskId> tasks);

    List<PhaseTimer> allPhasesOf(TaskId task);
}
