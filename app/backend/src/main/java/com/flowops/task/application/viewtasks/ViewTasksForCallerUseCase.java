package com.flowops.task.application.viewtasks;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ViewTasksForCallerUseCase {
    List<Row> visibleToCaller();

    record Row(UUID id, String title, String state, UUID assigneeId, String assigneeName, Instant deadline) {}
}
