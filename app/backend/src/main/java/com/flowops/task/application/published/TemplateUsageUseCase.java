package com.flowops.task.application.published;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TemplateUsageUseCase {
    Usage of(UUID templateId);

    record Usage(int stamped, LiveCounts live, ActiveTime active, FirstTryApproval approval) {}

    record LiveCounts(int notStarted, int running, int blocked, int inReview, int finished, int overdue) {}

    record ActiveTime(Long medianSeconds, Long lowerQuartileSeconds, Long upperQuartileSeconds, int measured) {}

    record FirstTryApproval(int passedFirstTime, int reviewed) {}

    List<UUID> stampedTaskIds(UUID templateId);

    List<TaskRow> tasksIn(UUID templateId, LiveBand band);

    enum LiveBand {
        NOT_STARTED,
        RUNNING,
        BLOCKED,
        IN_REVIEW,
        FINISHED,
        OVERDUE
    }

    record TaskRow(UUID taskId, String title, UUID assigneeId, String assigneeName, String state, Instant deadline) {}
}
