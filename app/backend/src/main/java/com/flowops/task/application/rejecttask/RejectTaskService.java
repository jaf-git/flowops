package com.flowops.task.application.rejecttask;

import com.flowops.task.application.shared.TaskTransitionResult;
import com.flowops.task.application.shared.TaskTransitionSupport;
import com.flowops.task.application.shared.port.LoadDeadlineProposalPort;
import com.flowops.task.application.shared.port.NotifyNegotiationPort;
import com.flowops.task.application.shared.port.SaveDeadlineProposalPort;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.TaskMove;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RejectTaskService implements RejectTaskUseCase {
    private final TaskTransitionSupport transitions;
    private final LoadDeadlineProposalPort loadDeadlineProposalPort;
    private final SaveDeadlineProposalPort saveDeadlineProposalPort;
    private final NotifyNegotiationPort notifyNegotiationPort;

    public RejectTaskService(
            TaskTransitionSupport transitions,
            LoadDeadlineProposalPort loadDeadlineProposalPort,
            SaveDeadlineProposalPort saveDeadlineProposalPort,
            NotifyNegotiationPort notifyNegotiationPort) {
        this.transitions = transitions;
        this.loadDeadlineProposalPort = loadDeadlineProposalPort;
        this.saveDeadlineProposalPort = saveDeadlineProposalPort;
        this.notifyNegotiationPort = notifyNegotiationPort;
    }

    @Override
    @Transactional
    public TaskTransitionResult execute(RejectTaskCommand command) {
        TaskTransitionSupport.InFlight inFlight = transitions.claim(command.task());

        PersonId assigner = inFlight.task().creator();

        loadDeadlineProposalPort
                .lockOpenProposalOf(command.task())
                .ifPresent(open -> saveDeadlineProposalPort.update(
                        open.declined(inFlight.assignee(), command.reason(), inFlight.now())));

        TaskTransitionResult result = transitions.applyWithNewAssignee(
                TaskMove.rejected(inFlight.task(), inFlight.openPhase(), command.reason(), inFlight.now()));

        notifyNegotiationPort.rejected(inFlight.task().id(), assigner, command.reason());
        return result;
    }
}
