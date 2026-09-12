package com.flowops.process.application.shared.port;

import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.StepId;
import com.flowops.process.domain.model.TaskRef;
import com.flowops.process.domain.model.TaskTemplateRef;
import java.time.Instant;

public interface CreateTaskPort {
    TaskRef createFor(
            InstanceId instance,
            StepId step,
            String title,
            String description,
            PersonId assignee,
            PersonId creator,
            Instant deadline,
            String priority,
            TaskTemplateRef work);
}
