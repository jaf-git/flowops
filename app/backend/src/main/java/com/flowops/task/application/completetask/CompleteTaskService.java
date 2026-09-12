package com.flowops.task.application.completetask;

import com.flowops.task.application.shared.TaskTransitionResult;
import com.flowops.task.application.shared.TaskTransitionSupport;
import com.flowops.task.application.shared.port.NotifyTaskProgressPort;
import com.flowops.task.application.shared.port.SaveCompletionProofPort;
import com.flowops.task.domain.model.CompletionProof;
import com.flowops.task.domain.model.TaskMove;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompleteTaskService implements CompleteTaskUseCase {
    private final TaskTransitionSupport transitions;
    private final SaveCompletionProofPort saveCompletionProofPort;
    private final NotifyTaskProgressPort notifyTaskProgressPort;

    public CompleteTaskService(
            TaskTransitionSupport transitions,
            SaveCompletionProofPort saveCompletionProofPort,
            NotifyTaskProgressPort notifyTaskProgressPort) {
        this.transitions = transitions;
        this.saveCompletionProofPort = saveCompletionProofPort;
        this.notifyTaskProgressPort = notifyTaskProgressPort;
    }

    @Override
    @Transactional
    public TaskTransitionResult execute(CompleteTaskCommand command) {
        TaskTransitionSupport.InFlight inFlight = transitions.claim(command.task());

        CompletionProof proof =
                CompletionProof.of(inFlight.task().id(), command.note(), command.externalLink(), inFlight.now());

        TaskTransitionResult result =
                transitions.apply(TaskMove.completed(inFlight.task(), inFlight.openPhase(), inFlight.now()));
        saveCompletionProofPort.save(proof);

        notifyTaskProgressPort.completionSubmitted(inFlight.task().id(), inFlight.assignee());
        return result;
    }
}
