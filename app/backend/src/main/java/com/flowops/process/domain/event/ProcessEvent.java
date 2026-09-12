package com.flowops.process.domain.event;

import com.flowops.process.domain.enums.ProcessAction;
import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.TemplateId;
import java.time.Instant;
import java.util.UUID;

public record ProcessEvent(
        UUID id, TemplateId template, InstanceId instance, ProcessAction action, PersonId actor, Instant occurredAt) {
    public static ProcessEvent onTemplate(TemplateId template, ProcessAction action, PersonId actor, Instant at) {
        return new ProcessEvent(UUID.randomUUID(), template, null, action, actor, at);
    }

    public static ProcessEvent onInstance(InstanceId instance, ProcessAction action, PersonId actor, Instant at) {
        return new ProcessEvent(UUID.randomUUID(), null, instance, action, actor, at);
    }
}
