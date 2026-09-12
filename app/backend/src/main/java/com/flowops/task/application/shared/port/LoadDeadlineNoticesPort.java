package com.flowops.task.application.shared.port;

import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.TaskId;
import java.time.Instant;
import java.util.List;

public interface LoadDeadlineNoticesPort {
    List<Notice> unacknowledgedFor(PersonId creator);

    record Notice(TaskId task, String title, PersonId assignee, Instant deadline, Instant setAt) {}
}
