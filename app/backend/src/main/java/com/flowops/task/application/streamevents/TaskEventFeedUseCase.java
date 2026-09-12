package com.flowops.task.application.streamevents;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TaskEventFeedUseCase {
    long currentCursor();

    List<TaskEventRecord> since(long cursor, int limit);

    record TaskEventRecord(long sequence, UUID task, String action, Instant at) {}
}
