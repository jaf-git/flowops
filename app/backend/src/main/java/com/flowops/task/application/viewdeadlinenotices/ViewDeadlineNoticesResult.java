package com.flowops.task.application.viewdeadlinenotices;

import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.TaskId;
import java.time.Instant;
import java.util.List;

public record ViewDeadlineNoticesResult(List<Notice> notices) {
    public record Notice(
            TaskId task, String title, PersonId assignee, String assigneeName, Instant deadline, Instant setAt) {}
}
