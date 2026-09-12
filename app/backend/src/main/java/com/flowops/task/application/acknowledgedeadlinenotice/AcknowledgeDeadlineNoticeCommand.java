package com.flowops.task.application.acknowledgedeadlinenotice;

import com.flowops.task.domain.model.TaskId;

public record AcknowledgeDeadlineNoticeCommand(TaskId task) {}
