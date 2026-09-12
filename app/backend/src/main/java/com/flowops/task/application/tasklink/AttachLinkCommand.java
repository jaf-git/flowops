package com.flowops.task.application.tasklink;

import com.flowops.task.domain.enums.LinkRole;
import com.flowops.task.domain.model.TaskId;

public record AttachLinkCommand(TaskId task, String url, String label, LinkRole role) {}
