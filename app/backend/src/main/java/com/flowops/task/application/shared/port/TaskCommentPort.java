package com.flowops.task.application.shared.port;

import com.flowops.task.domain.model.TaskComment;
import com.flowops.task.domain.model.TaskId;
import java.util.List;

public interface TaskCommentPort {
    void append(TaskComment comment);

    List<TaskComment> allOf(TaskId task);
}
