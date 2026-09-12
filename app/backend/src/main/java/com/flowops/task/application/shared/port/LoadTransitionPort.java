package com.flowops.task.application.shared.port;

import com.flowops.task.domain.model.StateTransition;
import com.flowops.task.domain.model.TaskId;
import java.util.List;
import java.util.Optional;

public interface LoadTransitionPort {
    Optional<String> currentBlockReasonOf(TaskId task);

    List<StateTransition> allOf(TaskId task);
}
