package com.flowops.canvas.application.shared.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TaskEventFeedPort {
    long currentCursor();

    List<Event> after(long cursor, int limit);

    record Event(long sequence, UUID task, String kind, Instant at) {}
}
