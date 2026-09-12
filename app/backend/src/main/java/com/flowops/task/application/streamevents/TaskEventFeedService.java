package com.flowops.task.application.streamevents;

import com.flowops.task.application.shared.port.ReadTaskEventFeedPort;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskEventFeedService implements TaskEventFeedUseCase {
    private final ReadTaskEventFeedPort readTaskEventFeedPort;

    public TaskEventFeedService(ReadTaskEventFeedPort readTaskEventFeedPort) {
        this.readTaskEventFeedPort = readTaskEventFeedPort;
    }

    @Override
    @Transactional(readOnly = true)
    public long currentCursor() {
        return readTaskEventFeedPort.latestSequence();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskEventRecord> since(long cursor, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return readTaskEventFeedPort.after(cursor, limit);
    }
}
