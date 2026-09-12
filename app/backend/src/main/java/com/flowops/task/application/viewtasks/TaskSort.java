package com.flowops.task.application.viewtasks;

import java.util.Comparator;

public enum TaskSort {
    DEADLINE("deadline"),

    PRIORITY("priority"),

    RECENT("recent");

    private final String key;

    TaskSort(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static TaskSort byKeyOrDefault(String candidate) {
        for (TaskSort sort : values()) {
            if (sort.key.equals(candidate)) {
                return sort;
            }
        }
        return DEADLINE;
    }

    Comparator<ViewTasksResult.Row> comparator() {
        Comparator<ViewTasksResult.Row> byIdentifier =
                Comparator.comparing(row -> row.id().value().toString());
        return switch (this) {
            case DEADLINE -> byDeadline().thenComparing(byIdentifier);

            case PRIORITY -> Comparator.comparing(ViewTasksResult.Row::priority)
                    .reversed()
                    .thenComparing(byDeadline())
                    .thenComparing(byIdentifier);

            case RECENT -> Comparator.comparing(
                            ViewTasksResult.Row::phaseSince, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(byIdentifier);
        };
    }

    private static Comparator<ViewTasksResult.Row> byDeadline() {
        return Comparator.comparing(ViewTasksResult.Row::deadline, Comparator.nullsLast(Comparator.naturalOrder()));
    }
}
