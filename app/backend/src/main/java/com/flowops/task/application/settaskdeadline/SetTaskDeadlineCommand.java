package com.flowops.task.application.settaskdeadline;

import com.flowops.task.domain.model.TaskId;
import java.time.Instant;

public record SetTaskDeadlineCommand(TaskId task, Instant deadline) {}
