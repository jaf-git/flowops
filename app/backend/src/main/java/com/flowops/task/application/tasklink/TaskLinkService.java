package com.flowops.task.application.tasklink;

import com.flowops.task.application.shared.TaskMaterialSupport;
import com.flowops.task.application.shared.exception.TaskNotFoundException;
import com.flowops.task.application.shared.port.AppendTaskEventPort;
import com.flowops.task.application.shared.port.TaskMaterialPort;
import com.flowops.task.domain.event.TaskEvent;
import com.flowops.task.domain.model.LinkUrl;
import com.flowops.task.domain.model.TaskLink;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskLinkService implements TaskLinkUseCase {
    private final TaskMaterialSupport material;
    private final TaskMaterialPort taskMaterialPort;
    private final AppendTaskEventPort appendTaskEventPort;

    public TaskLinkService(
            TaskMaterialSupport material, TaskMaterialPort taskMaterialPort, AppendTaskEventPort appendTaskEventPort) {
        this.material = material;
        this.taskMaterialPort = taskMaterialPort;
        this.appendTaskEventPort = appendTaskEventPort;
    }

    @Override
    @Transactional
    public TaskLink attach(AttachLinkCommand command) {
        TaskMaterialSupport.InHand inHand = material.claimForLinks(command.task());

        TaskLink link = TaskLink.attached(
                inHand.task().id(),
                LinkUrl.of(command.url()),
                command.label(),
                command.role(),
                inHand.actor(),
                inHand.now());

        taskMaterialPort.attach(link);
        appendTaskEventPort.append(TaskEvent.linkAttached(inHand.task().id(), inHand.actor(), inHand.now()));
        return link;
    }

    @Override
    @Transactional
    public void detach(DetachLinkCommand command) {
        TaskMaterialSupport.InHand inHand = material.claimForLinks(command.task());

        TaskLink link = taskMaterialPort
                .findLink(inHand.task().id(), command.link())
                .orElseThrow(() -> new TaskNotFoundException("there is no such link on this task"));

        taskMaterialPort.detach(inHand.task().id(), link.id());
        appendTaskEventPort.append(TaskEvent.linkDetached(inHand.task().id(), inHand.actor(), inHand.now()));
    }
}
