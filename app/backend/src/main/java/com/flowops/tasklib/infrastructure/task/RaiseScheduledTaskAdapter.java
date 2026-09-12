package com.flowops.tasklib.infrastructure.task;

import com.flowops.task.application.createtask.CreateScheduledTaskUseCase;
import com.flowops.tasklib.application.port.RaiseScheduledTaskPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RaiseScheduledTaskAdapter implements RaiseScheduledTaskPort {
    private final CreateScheduledTaskUseCase createScheduledTask;

    public RaiseScheduledTaskAdapter(CreateScheduledTaskUseCase createScheduledTask) {
        this.createScheduledTask = createScheduledTask;
    }

    @Override
    public UUID raise(ScheduledTask task) {
        return createScheduledTask.execute(new CreateScheduledTaskUseCase.NewScheduledTask(
                task.title(),
                task.description(),
                task.assigneeId(),
                task.creatorId(),
                task.deadline(),
                task.priority(),
                task.templateId(),
                task.stampedEstimatedHours()));
    }
}
