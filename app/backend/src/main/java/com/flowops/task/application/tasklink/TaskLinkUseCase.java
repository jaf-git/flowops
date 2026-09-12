package com.flowops.task.application.tasklink;

import com.flowops.task.domain.model.TaskLink;

public interface TaskLinkUseCase {
    TaskLink attach(AttachLinkCommand command);

    void detach(DetachLinkCommand command);
}
