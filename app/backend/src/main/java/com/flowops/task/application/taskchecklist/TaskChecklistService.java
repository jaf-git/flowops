package com.flowops.task.application.taskchecklist;

import com.flowops.task.application.shared.TaskMaterialSupport;
import com.flowops.task.application.shared.exception.TaskNotFoundException;
import com.flowops.task.application.shared.port.AppendTaskEventPort;
import com.flowops.task.application.shared.port.TaskMaterialPort;
import com.flowops.task.domain.event.TaskEvent;
import com.flowops.task.domain.model.ChecklistItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskChecklistService implements TaskChecklistUseCase {
    private final TaskMaterialSupport material;
    private final TaskMaterialPort taskMaterialPort;
    private final AppendTaskEventPort appendTaskEventPort;

    public TaskChecklistService(
            TaskMaterialSupport material, TaskMaterialPort taskMaterialPort, AppendTaskEventPort appendTaskEventPort) {
        this.material = material;
        this.taskMaterialPort = taskMaterialPort;
        this.appendTaskEventPort = appendTaskEventPort;
    }

    @Override
    @Transactional
    public ChecklistItem add(AddChecklistItemCommand command) {
        TaskMaterialSupport.InHand inHand = material.claimForLinks(command.task());

        ChecklistItem item = ChecklistItem.written(
                inHand.task().id(),
                taskMaterialPort.nextPosition(inHand.task().id()),
                command.text(),
                inHand.actor(),
                inHand.now());

        taskMaterialPort.add(item);
        appendTaskEventPort.append(TaskEvent.checklistItemAdded(inHand.task().id(), inHand.actor(), inHand.now()));
        return item;
    }

    @Override
    @Transactional
    public ChecklistItem tick(TickChecklistItemCommand command) {
        TaskMaterialSupport.InHand inHand = material.claimForTicking(command.task());

        ChecklistItem item = taskMaterialPort
                .findItem(inHand.task().id(), command.item())
                .orElseThrow(() -> new TaskNotFoundException("there is no such step on this task"));

        ChecklistItem ticked = item.ticked(command.done(), inHand.now());
        taskMaterialPort.update(ticked);
        appendTaskEventPort.append(TaskEvent.checklistItemTicked(inHand.task().id(), inHand.actor(), inHand.now()));
        return ticked;
    }

    @Override
    @Transactional
    public void remove(RemoveChecklistItemCommand command) {
        TaskMaterialSupport.InHand inHand = material.claimForLinks(command.task());

        ChecklistItem item = taskMaterialPort
                .findItem(inHand.task().id(), command.item())
                .orElseThrow(() -> new TaskNotFoundException("there is no such step on this task"));

        taskMaterialPort.remove(inHand.task().id(), item.id());
        appendTaskEventPort.append(TaskEvent.checklistItemRemoved(inHand.task().id(), inHand.actor(), inHand.now()));
    }
}
