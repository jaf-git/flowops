package com.flowops.process.infrastructure.task;

import com.flowops.process.application.shared.port.CreateTaskPort;
import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.StepId;
import com.flowops.process.domain.model.TaskRef;
import com.flowops.process.domain.model.TaskTemplateRef;
import com.flowops.task.application.createtask.CreateProcessTaskUseCase;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class TaskCreationAdapter implements CreateTaskPort {
    private final CreateProcessTaskUseCase createProcessTaskUseCase;

    public TaskCreationAdapter(CreateProcessTaskUseCase createProcessTaskUseCase) {
        this.createProcessTaskUseCase = createProcessTaskUseCase;
    }

    @Override
    public TaskRef createFor(
            InstanceId instance,
            StepId step,
            String title,
            String description,
            PersonId assignee,
            PersonId creator,
            Instant deadline,
            String priority,
            TaskTemplateRef work) {
        return TaskRef.of(createProcessTaskUseCase.execute(new CreateProcessTaskUseCase.NewProcessTask(
                title,
                description,
                assignee.value(),
                creator.value(),
                deadline,
                priority,
                instance.value(),
                step.value(),
                work == null ? null : work.value())));
    }
}
