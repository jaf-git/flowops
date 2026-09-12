package com.flowops.task.application.shared.port;

import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskId;
import java.time.Instant;

public interface SaveTaskPort {
    void create(Task task);

    void createForProcess(Task task, java.util.UUID instanceId, java.util.UUID stepId);

    void updateState(Task task);

    void updateStateAndAssignee(Task task);

    void updateFields(Task task);

    void updateDeadline(Task task);

    void acknowledgeDeadline(TaskId task, Instant at);

    void setProcessProvenance(java.util.UUID task, java.util.UUID instance, java.util.UUID step);

    void setTemplateProvenance(java.util.UUID task, java.util.UUID template);
}
