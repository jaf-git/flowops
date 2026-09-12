package com.flowops.task.application.shared.port;

import com.flowops.task.domain.model.Approval;
import com.flowops.task.domain.model.TaskId;
import java.util.Optional;

public interface LoadApprovalPort {
    Optional<Approval> findByTask(TaskId task);
}
