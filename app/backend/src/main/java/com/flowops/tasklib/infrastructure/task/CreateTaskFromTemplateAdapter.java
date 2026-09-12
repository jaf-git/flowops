package com.flowops.tasklib.infrastructure.task;

import com.flowops.task.application.createtask.CreateTaskCommand;
import com.flowops.task.application.createtask.CreateTaskUseCase;
import com.flowops.tasklib.application.port.CreateTaskFromTemplatePort;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CreateTaskFromTemplateAdapter implements CreateTaskFromTemplatePort {
    private final CreateTaskUseCase createTask;

    public CreateTaskFromTemplateAdapter(CreateTaskUseCase createTask) {
        this.createTask = createTask;
    }

    @Override
    public UUID createFromTemplate(NewTask task) {
        return createTask
                .execute(new CreateTaskCommand(
                        task.title(),
                        task.description(),
                        task.assigneeId(),
                        task.deadline(),
                        task.priority(),
                        task.templateId(),
                        task.stampedEstimatedHours(),
                        "TASK"))
                .taskId();
    }
}
