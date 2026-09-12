package com.flowops.task.application.edittask;

import com.flowops.task.application.shared.AtRiskRule;
import com.flowops.task.application.shared.TaskAuthorshipSupport;
import com.flowops.task.application.shared.TaskTransitionResult;
import com.flowops.task.application.shared.TaskWriter;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.NotifyNegotiationPort;
import com.flowops.task.application.shared.port.SaveAmendmentPort;
import com.flowops.task.domain.enums.TaskAction;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskAmendment;
import com.flowops.task.domain.model.TaskMove;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EditTaskService implements EditTaskUseCase {
    private final TaskAuthorshipSupport authorship;
    private final TaskWriter taskWriter;
    private final SaveAmendmentPort saveAmendmentPort;
    private final NotifyNegotiationPort notifyNegotiationPort;
    private final LoadPersonPort loadPersonPort;
    private final AtRiskRule atRiskRule;

    public EditTaskService(
            TaskAuthorshipSupport authorship,
            TaskWriter taskWriter,
            SaveAmendmentPort saveAmendmentPort,
            NotifyNegotiationPort notifyNegotiationPort,
            LoadPersonPort loadPersonPort,
            AtRiskRule atRiskRule) {
        this.authorship = authorship;
        this.taskWriter = taskWriter;
        this.saveAmendmentPort = saveAmendmentPort;
        this.notifyNegotiationPort = notifyNegotiationPort;
        this.loadPersonPort = loadPersonPort;
        this.atRiskRule = atRiskRule;
    }

    @Override
    @Transactional
    public TaskTransitionResult execute(EditTaskCommand command) {
        TaskAuthorshipSupport.InHand inHand = authorship.claim(command.task());
        Task task = inHand.task();

        Task edited = task.amended(command.deadline(), command.priority(), command.description(), inHand.now());

        Task amended = edited.deadline() != null && !edited.deadline().equals(task.deadline())
                ? edited.withDeadline(edited.deadline(), inHand.actor(), inHand.now())
                : edited;

        TaskMove move = TaskMove.amended(task, amended, inHand.actor(), TaskAction.TASK_EDITED, null, inHand.now());
        taskWriter.writeAmendment(move);

        TaskAmendment whatMoved =
                TaskAmendment.between(task, amended, move.event().id(), inHand.actor(), inHand.now());
        saveAmendmentPort.save(whatMoved);

        if (whatMoved.deadlineMoved()) {
            amended.assignee()
                    .ifPresent(assignee -> notifyNegotiationPort.deadlineChanged(
                            amended.id(), assignee, whatMoved.formerDeadline(), whatMoved.newDeadline()));
        }

        return new TaskTransitionResult(
                amended,
                amended.assignee()
                        .flatMap(loadPersonPort::describe)
                        .map(LoadPersonPort.Person::displayName)
                        .orElse(""),
                atRiskRule.of(amended, command.deadline(), inHand.now()));
    }
}
