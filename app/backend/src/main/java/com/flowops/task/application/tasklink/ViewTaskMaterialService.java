package com.flowops.task.application.tasklink;

import com.flowops.task.application.shared.TaskMaterialSupport;
import com.flowops.task.application.shared.port.TaskMaterialPort;
import com.flowops.task.domain.model.TaskId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewTaskMaterialService implements ViewTaskMaterialUseCase {
    private final TaskMaterialSupport material;
    private final TaskMaterialPort taskMaterialPort;

    public ViewTaskMaterialService(TaskMaterialSupport material, TaskMaterialPort taskMaterialPort) {
        this.material = material;
        this.taskMaterialPort = taskMaterialPort;
    }

    @Override
    @Transactional(readOnly = true)
    public Material execute(TaskId task) {
        TaskMaterialSupport.InHand inHand = material.claimForLinks(task);
        return new Material(
                taskMaterialPort.linksOf(inHand.task().id()),
                taskMaterialPort.checklistOf(inHand.task().id()));
    }
}
