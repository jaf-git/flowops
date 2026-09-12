package com.flowops.task.application.shared.port;

import com.flowops.task.domain.model.TaskAmendment;

public interface SaveAmendmentPort {
    void save(TaskAmendment amendment);
}
