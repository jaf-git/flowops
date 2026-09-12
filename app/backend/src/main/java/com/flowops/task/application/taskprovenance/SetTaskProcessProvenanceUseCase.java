package com.flowops.task.application.taskprovenance;

import java.util.UUID;

public interface SetTaskProcessProvenanceUseCase {
    void link(UUID task, UUID instance, UUID step);

    void unlink(UUID task);
}
