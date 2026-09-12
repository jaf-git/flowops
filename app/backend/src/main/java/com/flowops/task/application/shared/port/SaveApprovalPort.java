package com.flowops.task.application.shared.port;

import com.flowops.task.domain.model.Approval;

public interface SaveApprovalPort {
    void save(Approval approval);
}
