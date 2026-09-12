package com.flowops.process.application.viewinstance;

import com.flowops.process.application.shared.port.TaskStatePort;
import com.flowops.process.domain.model.ProcessInstance;
import com.flowops.process.domain.model.TaskRef;
import java.util.Map;

public record InstanceView(
        ProcessInstance instance, Map<TaskRef, TaskStatePort.TaskProgress> progress, String templateName) {}
