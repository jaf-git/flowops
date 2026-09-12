package com.flowops.task.application.starttask;

import com.flowops.task.application.shared.TaskTransitionResult;
import com.flowops.task.application.shared.TaskTransitionSupport;
import com.flowops.task.domain.model.TaskMove;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StartTaskService implements StartTaskUseCase {
    private final TaskTransitionSupport transitions;

    public StartTaskService(TaskTransitionSupport transitions) {
        this.transitions = transitions;
    }

    @Override
    @Transactional
    public TaskTransitionResult execute(StartTaskCommand command) {
        TaskTransitionSupport.InFlight inFlight = transitions.claim(command.task());
        return transitions.apply(TaskMove.started(inFlight.task(), inFlight.openPhase(), inFlight.now()));
    }
}
