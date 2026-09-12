package com.flowops.task.application.viewtasks;

import com.flowops.task.domain.enums.TaskState;
import java.time.Duration;
import java.time.Instant;

public enum TaskSection {
    NEEDS_YOU("needs-you"),

    OVERDUE("overdue"),

    DUE_SOON("due-soon"),

    IN_PROGRESS("in-progress"),

    WAITING("waiting"),

    NOT_STARTED("not-started"),

    ARCHIVE("archive");

    private final String key;

    TaskSection(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static TaskSection byKey(String candidate) {
        for (TaskSection section : values()) {
            if (section.key.equals(candidate)) {
                return section;
            }
        }
        return null;
    }

    public static TaskSection of(ViewTasksResult.Row row, Instant now, Duration soon) {
        if (row.state() == TaskState.CLOSED) {
            return ARCHIVE;
        }
        if (needsTheCaller(row)) {
            return NEEDS_YOU;
        }
        if (row.deadline() != null && row.deadline().isBefore(now)) {
            return OVERDUE;
        }
        if (row.deadline() != null && row.deadline().isBefore(now.plus(soon))) {
            return DUE_SOON;
        }
        if (row.state() == TaskState.IN_PROGRESS) {
            return IN_PROGRESS;
        }
        if (isWaiting(row)) {
            return WAITING;
        }
        return NOT_STARTED;
    }

    private static boolean needsTheCaller(ViewTasksResult.Row row) {
        boolean waitingOnMyReview = row.directedByMe() && row.state() == TaskState.COMPLETED;
        boolean offeredToMe = row.mine() && row.state() == TaskState.CREATED;
        boolean myAnswerOnADate = row.deadlineProposalOpen() && row.directedByMe();
        return waitingOnMyReview || offeredToMe || myAnswerOnADate;
    }

    private static boolean isWaiting(ViewTasksResult.Row row) {
        return row.state() == TaskState.BLOCKED
                || row.state() == TaskState.COMPLETED
                || row.state() == TaskState.APPROVED
                || row.deadlineProposalOpen();
    }
}
