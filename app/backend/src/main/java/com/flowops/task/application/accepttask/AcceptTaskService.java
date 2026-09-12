package com.flowops.task.application.accepttask;

import com.flowops.task.application.shared.TaskTransitionResult;
import com.flowops.task.application.shared.TaskTransitionSupport;
import com.flowops.task.domain.model.TaskMove;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcceptTaskService implements AcceptTaskUseCase {
    private final TaskTransitionSupport transitions;

    public AcceptTaskService(TaskTransitionSupport transitions) {
        this.transitions = transitions;
    }

    @Override
    @Transactional
    public AcceptTaskResult execute(AcceptTaskCommand command) {
        TaskTransitionSupport.InFlight inFlight = transitions.claim(command.task());
        TaskTransitionResult result =
                transitions.apply(TaskMove.acceptance(inFlight.task(), inFlight.openPhase(), inFlight.now()));
        return new AcceptTaskResult(result.task(), result.assigneeName(), result.atRisk());
    }
}
