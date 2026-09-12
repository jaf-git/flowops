package com.flowops.task.application.proposedeadline;

import com.flowops.task.domain.model.TaskId;
import java.time.Instant;

public record ProposeDeadlineCommand(TaskId task, Instant proposedDeadline, String reason) {}
