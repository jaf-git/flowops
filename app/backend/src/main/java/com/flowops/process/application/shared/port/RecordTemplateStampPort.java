package com.flowops.process.application.shared.port;

import com.flowops.process.domain.model.TaskTemplateRef;

public interface RecordTemplateStampPort {
    void stamped(TaskTemplateRef work);
}
