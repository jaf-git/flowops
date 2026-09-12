package com.flowops.task.application.commenttask;

import com.flowops.task.domain.model.TaskComment;
import com.flowops.task.domain.model.TaskId;

public interface CommentOnTaskUseCase {
    TaskComment execute(TaskId task, String body);
}
