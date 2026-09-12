package com.flowops.task.application.viewtasks;

import java.util.List;

public final class SectionedTasksResult {
    private SectionedTasksResult() {}

    public record Counts(List<SectionCount> sections, int total) {}

    public record SectionCount(TaskSection section, int count) {}

    public record Page(TaskSection section, List<ViewTasksResult.Row> rows, int page, int size, int total) {
        public int totalPages() {
            return Math.max(1, (int) Math.ceil((double) total / size));
        }
    }
}
