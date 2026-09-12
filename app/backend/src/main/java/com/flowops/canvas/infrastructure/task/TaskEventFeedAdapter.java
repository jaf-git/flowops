package com.flowops.canvas.infrastructure.task;

import com.flowops.canvas.application.shared.port.TaskEventFeedPort;
import com.flowops.task.application.streamevents.TaskEventFeedUseCase;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TaskEventFeedAdapter implements TaskEventFeedPort {
    private final TaskEventFeedUseCase feed;

    public TaskEventFeedAdapter(TaskEventFeedUseCase feed) {
        this.feed = feed;
    }

    @Override
    public long currentCursor() {
        return feed.currentCursor();
    }

    @Override
    public List<Event> after(long cursor, int limit) {
        return feed.since(cursor, limit).stream()
                .map(event -> new Event(event.sequence(), event.task(), event.action(), event.at()))
                .toList();
    }
}
