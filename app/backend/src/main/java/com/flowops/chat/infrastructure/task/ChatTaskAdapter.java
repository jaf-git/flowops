package com.flowops.chat.infrastructure.task;

import com.flowops.chat.application.shared.port.AssignableCheckPort;
import com.flowops.chat.application.shared.port.CreateTaskPort;
import com.flowops.task.application.viewassignablepeople.ViewAssignablePeopleUseCase;
import com.flowops.tasklib.application.TaskTemplateUseCase;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ChatTaskAdapter implements CreateTaskPort, AssignableCheckPort {
    private final TaskTemplateUseCase templates;
    private final ViewAssignablePeopleUseCase assignablePeople;

    public ChatTaskAdapter(TaskTemplateUseCase templates, ViewAssignablePeopleUseCase assignablePeople) {
        this.templates = templates;
        this.assignablePeople = assignablePeople;
    }

    @Override
    public boolean mayAssign(UUID person) {
        return assignablePeople.execute().stream()
                .anyMatch(candidate -> candidate.id().equals(person));
    }

    @Override
    public UUID createAdHoc(
            String title, String description, UUID assignee, Instant deadline, String priority, UUID template) {
        return templates
                .stampTask(
                        template,
                        new TaskTemplateUseCase.StampRequest(title, description, assignee, deadline, priority))
                .taskId();
    }
}
