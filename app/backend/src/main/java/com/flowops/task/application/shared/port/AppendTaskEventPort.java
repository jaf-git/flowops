package com.flowops.task.application.shared.port;

import com.flowops.task.domain.event.TaskEvent;

public interface AppendTaskEventPort {
    void append(TaskEvent event);
}
