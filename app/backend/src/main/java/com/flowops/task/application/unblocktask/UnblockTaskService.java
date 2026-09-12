package com.flowops.task.application.unblocktask;

import com.flowops.task.application.shared.TaskTransitionResult;
import com.flowops.task.application.shared.TaskTransitionSupport;
import com.flowops.task.domain.model.TaskMove;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnblockTaskService implements UnblockTaskUseCase {
    private final TaskTransitionSupport transitions;

    public UnblockTaskService(TaskTransitionSupport transitions) {
        this.transitions = transitions;
    }

    @Override
    @Transactional
    public TaskTransitionResult execute(UnblockTaskCommand command) {
        TaskTransitionSupport.InFlight inFlight = transitions.claim(command.task());
        return transitions.apply(
                TaskMove.unblocked(inFlight.task(), inFlight.openPhase(), command.resolution(), inFlight.now()));
    }
}
