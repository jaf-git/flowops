package com.flowops.process.application.shared.port;

import com.flowops.process.domain.model.TaskTemplateRef;
import com.flowops.process.domain.model.TaskTemplateWork;
import java.util.Collection;
import java.util.Map;

public interface TaskTemplateContentPort {
    Map<TaskTemplateRef, TaskTemplateWork> contentOf(Collection<TaskTemplateRef> templates);

    Map<TaskTemplateRef, TaskTemplateWork> namesFor(Collection<TaskTemplateRef> templates);
}
