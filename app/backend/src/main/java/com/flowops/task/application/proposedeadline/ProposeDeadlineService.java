package com.flowops.task.application.proposedeadline;

import com.flowops.task.application.shared.TaskTransitionResult;
import com.flowops.task.application.shared.TaskTransitionSupport;
import com.flowops.task.application.shared.port.LoadDeadlineProposalPort;
import com.flowops.task.application.shared.port.NotifyNegotiationPort;
import com.flowops.task.application.shared.port.SaveDeadlineProposalPort;
import com.flowops.task.domain.enums.TaskAction;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.exception.ProposalAlreadyOpenException;
import com.flowops.task.domain.model.DeadlineProposal;
import com.flowops.task.domain.model.TaskMove;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProposeDeadlineService implements ProposeDeadlineUseCase {
    private final TaskTransitionSupport transitions;
    private final LoadDeadlineProposalPort loadDeadlineProposalPort;
    private final SaveDeadlineProposalPort saveDeadlineProposalPort;
    private final NotifyNegotiationPort notifyNegotiationPort;

    public ProposeDeadlineService(
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
    public TaskTransitionResult execute(ProposeDeadlineCommand command) {
        TaskTransitionSupport.InFlight inFlight = transitions.claim(command.task());

        inFlight.task().state().moveTo(TaskState.CREATED);

        loadDeadlineProposalPort.lockOpenProposalOf(command.task()).ifPresent(open -> {
            throw new ProposalAlreadyOpenException(open.proposedDeadline());
        });

        DeadlineProposal proposal = DeadlineProposal.proposed(
                command.task(), command.proposedDeadline(), command.reason(), inFlight.assignee(), inFlight.now());

        saveDeadlineProposalPort.save(proposal);
        TaskTransitionResult result = transitions.record(TaskMove.amended(
                inFlight.task(),
                inFlight.task(),
                inFlight.assignee(),
                TaskAction.DEADLINE_PROPOSED,
                command.reason(),
                inFlight.now()));

        notifyNegotiationPort.deadlineProposed(
                command.task(), inFlight.task().creator(), command.proposedDeadline(), command.reason());
        return result;
    }
}
