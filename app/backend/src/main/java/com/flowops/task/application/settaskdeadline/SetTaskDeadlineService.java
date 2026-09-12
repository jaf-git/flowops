package com.flowops.task.application.settaskdeadline;

import com.flowops.task.application.shared.TaskTransitionResult;
import com.flowops.task.application.shared.TaskTransitionSupport;
import com.flowops.task.application.shared.port.SaveAmendmentPort;
import com.flowops.task.application.shared.port.SaveTaskPort;
import com.flowops.task.domain.enums.TaskAction;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.exception.IllegalTransitionException;
import com.flowops.task.domain.exception.NothingChangedException;
import com.flowops.task.domain.exception.UseAProposalInsteadException;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskAmendment;
import com.flowops.task.domain.model.TaskMove;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SetTaskDeadlineService implements SetTaskDeadlineUseCase {
    private final TaskTransitionSupport transitions;
    private final SaveAmendmentPort saveAmendmentPort;
    private final SaveTaskPort saveTaskPort;

    public SetTaskDeadlineService(
            TaskTransitionSupport transitions, SaveAmendmentPort saveAmendmentPort, SaveTaskPort saveTaskPort) {
        this.transitions = transitions;
        this.saveAmendmentPort = saveAmendmentPort;
        this.saveTaskPort = saveTaskPort;
    }

    @Override
    @Transactional
    public TaskTransitionResult execute(SetTaskDeadlineCommand command) {
        TaskTransitionSupport.InFlight inFlight = transitions.claim(command.task());
        Task task = inFlight.task();

        refuseOutsideAccepted(task);

        if (command.deadline().equals(task.deadline())) {
            throw new NothingChangedException();
        }

        Task dated = task.withDeadline(command.deadline(), inFlight.assignee(), inFlight.now());

        saveTaskPort.updateDeadline(dated);

        TaskMove move =
                TaskMove.amended(task, dated, inFlight.assignee(), TaskAction.DEADLINE_SET, null, inFlight.now());
        transitions.writeAmendment(move);
        saveAmendmentPort.save(
                TaskAmendment.between(task, dated, move.event().id(), inFlight.assignee(), inFlight.now()));

        return transitions.resultOf(dated, command.deadline(), inFlight.now());
    }

    private void refuseOutsideAccepted(Task task) {
        if (task.state() == TaskState.ACCEPTED) {
            return;
        }
        if (task.state() == TaskState.CREATED) {
            throw new IllegalTransitionException(TaskState.CREATED, TaskState.ACCEPTED);
        }
        throw new UseAProposalInsteadException();
    }
}
