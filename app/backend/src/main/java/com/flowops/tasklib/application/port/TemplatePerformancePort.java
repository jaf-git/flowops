package com.flowops.tasklib.application.port;

import com.flowops.tasklib.domain.TemplatePerformance;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TemplatePerformancePort {
    TemplatePerformance performanceOf(UUID templateId);

    LiveWork liveWorkFor(UUID templateId);

    List<Row> tasksIn(UUID templateId, String band);

    record LiveWork(int notStarted, int running, int blocked, int inReview, int finished, int overdue) {}

    record Row(UUID taskId, String title, UUID assigneeId, String assigneeName, String state, Instant deadline) {}
}
