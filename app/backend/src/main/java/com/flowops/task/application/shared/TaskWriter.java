package com.flowops.task.application.shared;

import com.flowops.shared.event.TaskStateChanged;
import com.flowops.task.application.shared.port.AppendTaskEventPort;
import com.flowops.task.application.shared.port.PhaseTimerPort;
import com.flowops.task.application.shared.port.RecordTransitionPort;
import com.flowops.task.application.shared.port.SaveTaskPort;
import com.flowops.task.domain.model.TaskMove;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class TaskWriter {
    private final SaveTaskPort saveTaskPort;
    private final PhaseTimerPort phaseTimerPort;
    private final RecordTransitionPort recordTransitionPort;
    private final AppendTaskEventPort appendTaskEventPort;
    private final ApplicationEventPublisher announcements;

    public TaskWriter(
            SaveTaskPort saveTaskPort,
            PhaseTimerPort phaseTimerPort,
            RecordTransitionPort recordTransitionPort,
            AppendTaskEventPort appendTaskEventPort,
            ApplicationEventPublisher announcements) {
        this.announcements = announcements;
        this.saveTaskPort = saveTaskPort;
        this.phaseTimerPort = phaseTimerPort;
        this.recordTransitionPort = recordTransitionPort;
        this.appendTaskEventPort = appendTaskEventPort;
    }

    public void writeProcessCreation(TaskMove move, java.util.UUID instanceId, java.util.UUID stepId) {
        saveTaskPort.createForProcess(move.task(), instanceId, stepId);
        writeTheRest(move);
    }

    public void writeCreation(TaskMove move) {
        saveTaskPort.create(move.task());
        writeTheRest(move);
    }

    public void writeMove(TaskMove move) {
        saveTaskPort.updateState(move.task());
        writeTheRest(move);
    }

    public void writeStateAndAssignee(TaskMove move) {
        saveTaskPort.updateStateAndAssignee(move.task());
        writeTheRest(move);
    }

    public void writeAmendment(TaskMove move) {
        saveTaskPort.updateFields(move.task());
        writeTheRest(move);
    }

    public void writeRecord(TaskMove move) {
        writeTheRest(move);
    }

    private void writeTheRest(TaskMove move) {
        move.closedPhase().ifPresent(phaseTimerPort::close);
        move.openedPhase().ifPresent(phaseTimerPort::open);
        recordTransitionPort.record(move.transition());
        appendTaskEventPort.append(move.event());
        announce(move);
    }

    private void announce(TaskMove move) {
        announcements.publishEvent(new TaskStateChanged(
                move.task().id().value(),
                move.task().state().name(),
                move.task().assignee().isPresent(),
                move.transition().occurredAt()));
    }
}
