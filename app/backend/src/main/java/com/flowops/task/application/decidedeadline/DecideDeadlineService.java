package com.flowops.task.application.decidedeadline;

import com.flowops.task.application.shared.AtRiskRule;
import com.flowops.task.application.shared.TaskAuthorshipSupport;
import com.flowops.task.application.shared.TaskTransitionResult;
import com.flowops.task.application.shared.TaskWriter;
import com.flowops.task.application.shared.port.LoadDeadlineProposalPort;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.NotifyNegotiationPort;
import com.flowops.task.application.shared.port.SaveAmendmentPort;
import com.flowops.task.application.shared.port.SaveDeadlineProposalPort;
import com.flowops.task.domain.enums.TaskAction;
import com.flowops.task.domain.exception.NoOpenProposalException;
import com.flowops.task.domain.model.DeadlineProposal;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskAmendment;
import com.flowops.task.domain.model.TaskMove;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DecideDeadlineService implements DecideDeadlineUseCase {
    private final TaskAuthorshipSupport authorship;
    private final AtRiskRule atRiskRule;
    private final TaskWriter taskWriter;
    private final SaveAmendmentPort saveAmendmentPort;
    private final LoadPersonPort loadPersonPort;
    private final LoadDeadlineProposalPort loadDeadlineProposalPort;
    private final SaveDeadlineProposalPort saveDeadlineProposalPort;
    private final NotifyNegotiationPort notifyNegotiationPort;

    public DecideDeadlineService(
            TaskAuthorshipSupport authorship,
            AtRiskRule atRiskRule,
            TaskWriter taskWriter,
            SaveAmendmentPort saveAmendmentPort,
            LoadPersonPort loadPersonPort,
            LoadDeadlineProposalPort loadDeadlineProposalPort,
            SaveDeadlineProposalPort saveDeadlineProposalPort,
            NotifyNegotiationPort notifyNegotiationPort) {
        this.authorship = authorship;
        this.atRiskRule = atRiskRule;
        this.taskWriter = taskWriter;
        this.saveAmendmentPort = saveAmendmentPort;
        this.loadPersonPort = loadPersonPort;
        this.loadDeadlineProposalPort = loadDeadlineProposalPort;
        this.saveDeadlineProposalPort = saveDeadlineProposalPort;
        this.notifyNegotiationPort = notifyNegotiationPort;
    }

    @Override
    @Transactional
    public TaskTransitionResult execute(DecideDeadlineCommand command) {
        TaskAuthorshipSupport.InHand inHand = authorship.claim(command.task());

        DeadlineProposal open =
                loadDeadlineProposalPort.lockOpenProposalOf(command.task()).orElseThrow(NoOpenProposalException::new);

        Task task = inHand.task();

        if (!command.accept()) {
            saveDeadlineProposalPort.update(open.declined(inHand.actor(), command.reason(), inHand.now()));

            task.assignee()
                    .ifPresent(assignee ->
                            notifyNegotiationPort.deadlineProposalDeclined(task.id(), assignee, command.reason()));
            return new TaskTransitionResult(task, nameOf(task), atRiskRule.of(task));
        }

        DeadlineProposal agreed = open.accepted(inHand.actor(), inHand.now());
        saveDeadlineProposalPort.update(agreed);

        Task moved = task.withDeadline(open.proposedDeadline(), inHand.actor(), inHand.now());
        TaskMove amendment =
                TaskMove.amended(task, moved, inHand.actor(), TaskAction.DEADLINE_CHANGED, open.reason(), inHand.now());
        taskWriter.writeAmendment(amendment);
        saveAmendmentPort.save(
                TaskAmendment.between(task, moved, amendment.event().id(), inHand.actor(), inHand.now()));

        moved.assignee()
                .ifPresent(assignee ->
                        notifyNegotiationPort.deadlineProposalAccepted(task.id(), assignee, open.proposedDeadline()));
        return new TaskTransitionResult(
                moved, nameOf(moved), atRiskRule.of(moved, open.proposedDeadline(), inHand.now()));
    }

    private String nameOf(Task task) {
        return task.assignee()
                .flatMap(loadPersonPort::describe)
                .map(LoadPersonPort.Person::displayName)
                .orElse("");
    }
}
