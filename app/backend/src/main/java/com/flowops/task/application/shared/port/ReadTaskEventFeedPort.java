package com.flowops.task.application.shared.port;

import com.flowops.task.application.streamevents.TaskEventFeedUseCase.TaskEventRecord;
import java.util.List;

public interface ReadTaskEventFeedPort {
    long latestSequence();

    List<TaskEventRecord> after(long cursor, int limit);
}
