package com.flowops.task.application.blocktask;

import com.flowops.task.application.shared.TaskTransitionResult;
import com.flowops.task.application.shared.TaskTransitionSupport;
import com.flowops.task.application.shared.port.NotifyTaskProgressPort;
import com.flowops.task.domain.model.TaskMove;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockTaskService implements BlockTaskUseCase {
    private final TaskTransitionSupport transitions;
    private final NotifyTaskProgressPort notifyTaskProgressPort;

    public BlockTaskService(TaskTransitionSupport transitions, NotifyTaskProgressPort notifyTaskProgressPort) {
        this.transitions = transitions;
        this.notifyTaskProgressPort = notifyTaskProgressPort;
    }

    @Override
    @Transactional
    public TaskTransitionResult execute(BlockTaskCommand command) {
        TaskTransitionSupport.InFlight inFlight = transitions.claim(command.task());
        TaskTransitionResult result = transitions.apply(
                TaskMove.blocked(inFlight.task(), inFlight.openPhase(), command.reason(), inFlight.now()));

        notifyTaskProgressPort.blockRaised(inFlight.task().id(), inFlight.assignee(), command.reason());
        return result;
    }
}
