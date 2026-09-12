package com.flowops.task.application.viewtasks;

import com.flowops.task.domain.enums.TaskPriority;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public record TaskFilter(
        String query, UUID assignee, TaskPriority priority, Instant deadlineFrom, Instant deadlineTo, UUID category) {
    public static TaskFilter none() {
        return new TaskFilter(null, null, null, null, null, null);
    }

    public TaskFilter {
        query = query == null || query.isBlank() ? null : query.trim().toLowerCase(Locale.ROOT);
    }

    public boolean accepts(ViewTasksResult.Row row) {
        return matchesQuery(row)
                && matchesAssignee(row)
                && matchesPriority(row)
                && matchesDeadline(row)
                && matchesCategory(row);
    }

    private boolean matchesCategory(ViewTasksResult.Row row) {
        return category == null || category.equals(row.categoryId());
    }

    private boolean matchesQuery(ViewTasksResult.Row row) {
        return query == null || row.title().toLowerCase(Locale.ROOT).contains(query);
    }

    private boolean matchesAssignee(ViewTasksResult.Row row) {
        return assignee == null || assignee.equals(row.assigneeId());
    }

    private boolean matchesPriority(ViewTasksResult.Row row) {
        return priority == null || priority == row.priority();
    }

    private boolean matchesDeadline(ViewTasksResult.Row row) {
        if (deadlineFrom == null && deadlineTo == null) {
            return true;
        }
        if (row.deadline() == null) {
            return false;
        }
        return (deadlineFrom == null || !row.deadline().isBefore(deadlineFrom))
                && (deadlineTo == null || !row.deadline().isAfter(deadlineTo));
    }
}
