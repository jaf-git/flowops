package com.flowops.task.application.viewtasks;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SectionedTaskQueryService implements SectionedTaskQueryUseCase {
    private static final Duration DUE_SOON_REACHES = Duration.ofDays(7);

    private final ViewTasksUseCase viewTasks;
    private final Clock clock;

    public SectionedTaskQueryService(ViewTasksUseCase viewTasks, Clock clock) {
        this.viewTasks = viewTasks;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public SectionedTasksResult.Counts count(TaskFilter filter) {
        Map<TaskSection, Integer> tally = new EnumMap<>(TaskSection.class);
        for (TaskSection section : TaskSection.values()) {
            tally.put(section, 0);
        }

        int total = 0;
        Instant now = clock.instant();
        for (ViewTasksResult.Row row : viewTasks.execute().tasks()) {
            if (!filter.accepts(row)) {
                continue;
            }
            tally.merge(TaskSection.of(row, now, DUE_SOON_REACHES), 1, Integer::sum);
            total++;
        }

        List<SectionedTasksResult.SectionCount> sections = new ArrayList<>();
        for (TaskSection section : TaskSection.values()) {
            sections.add(new SectionedTasksResult.SectionCount(section, tally.get(section)));
        }
        return new SectionedTasksResult.Counts(sections, total);
    }

    @Override
    @Transactional(readOnly = true)
    public SectionedTasksResult.Page page(TaskSection section, TaskFilter filter, TaskSort sort, int page, int size) {
        Instant now = clock.instant();
        List<ViewTasksResult.Row> inSection = viewTasks.execute().tasks().stream()
                .filter(filter::accepts)
                .filter(row -> TaskSection.of(row, now, DUE_SOON_REACHES) == section)
                .sorted(sort.comparator())
                .toList();

        int from = Math.min(page * size, inSection.size());
        int to = Math.min(from + size, inSection.size());
        return new SectionedTasksResult.Page(section, inSection.subList(from, to), page, size, inSection.size());
    }
}
